package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
        priorityDefault = 1;
        priorityMinimum = 1;
        priorityMaximum = Integer.MAX_VALUE;
    }
    public static void selfTest()
    {
        boolean machine_start_status = Machine.interrupt().disabled();
        Machine.interrupt().disable();

        LotteryScheduler lot = new LotteryScheduler();
        ThreadQueue derp = lot.newThreadQueue(true);

        KThread k1 = new KThread();
        KThread k2 = new KThread();
        KThread k3 = new KThread();

        derp.acquire(k1);
        derp.waitForAccess(k2);
        derp.waitForAccess(k3);

        lot.setPriority(k2,67519053);
        lot.setPriority(k3,1);

        //derp.print();
        KThread pickedThread = derp.nextThread();
//        derp.print();
//        System.out.println("picked thread priority = " + lot.getEffectivePriority(pickedThread));
//        System.out.println("kicked out thread priority = " + lot.getEffectivePriority(k1));

        Machine.interrupt().restore(machine_start_status);
    }
    @Override
    protected ThreadState getThreadState(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());
        if (thread.schedulingState == null)
            thread.schedulingState = new LotteryThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        // implement me
        return new LotteryQueue(transferPriority);
    }


    protected class LotteryQueue extends PriorityQueue{
        LotteryQueue(boolean transferPriority)
        {
            this.transferPriority = transferPriority;
        }
        LotteryQueue()
        {
            this.transferPriority = false;
        }
        private class ticketInterval{
            ThreadState ticketOwner;
            int startInterval, endInterval;
            ticketInterval(ThreadState ticketOwner, int startInterval, int endInterval)
            {
                this.ticketOwner = ticketOwner;
                this.startInterval = startInterval;
                this.endInterval = endInterval;
            }

            public boolean contains(int ticket) {return (startInterval < ticket && ticket < endInterval) || startInterval == ticket || endInterval == ticket;}
        }
        @Override
        public ThreadState popNextThread()
        {
            if (threadStates.size() == 1){return threadStates.pollLast();}
            if (threadStates.isEmpty())return null;

            Iterator<ThreadState> it = threadStates.iterator();
            int maxRange = 0;
            HashSet<ticketInterval> threadTicketStates = new HashSet<ticketInterval>();
           


            while(it.hasNext())
            {
                ThreadState next = it.next();
                threadTicketStates.add(new ticketInterval(next,maxRange + 1,maxRange + next.getWinningPriority()));

                maxRange += next.getWinningPriority();
            }

            Random rand = new Random();
            int ticket = rand.nextInt((maxRange - 1) + 1) + 1;
            Iterator<ticketInterval> it2 = threadTicketStates.iterator();

            while(it2.hasNext())
            {
                ticketInterval temp = it2.next();

                if (temp.contains(ticket))
                {
                    threadStates.remove(temp.ticketOwner);
                    return temp.ticketOwner;
                }
            }

            return null;
        }

        @Override
        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (threadStates.isEmpty())
                return null;

            //Pulls the thread of highest priority and earliest time off the treeset
            ThreadState tState = (popNextThread());

            // placement value is set back to zero so priority scheduler can know that next time this
            // thread get's put into a new queue, that it needs to be given new placement value to
            // ordered by
            tState.placement = 0;
            KThread thread = tState.thread;


            if (thread != null)
            {
                if (this.owner != null)
                {
                    //Remove this from the old owners' queue
                    this.owner.ownedQueues.remove(this);


                    Iterator<ThreadState> it = threadStates.iterator();
                    while(it.hasNext() && transferPriority)
                    {
                        ThreadState temp = it.next();
                        this.owner.effectivePriority -= temp.getWinningPriority();
                    }
                    
                    this.owner.update();

                }

                //Now thread is going to run, so it should acquire this waitQueue and it shouldnt be waiting on anything else
                ((ThreadState)thread.schedulingState).waitingQueue = null;
                ((ThreadState) thread.schedulingState).acquire(this);
            }

            return thread;
        }


    }
    protected class LotteryThreadState extends ThreadState
    {
        LotteryThreadState(KThread thread)
        {
            super(thread);
            priorityCopy = priority;
            effectivePriorityCopy = effectivePriority;
        }


        @Override
        public void waitForAccess(PriorityQueue waitQueue)
        {
            Lib.assertTrue(Machine.interrupt().disabled());
            Lib.assertTrue(waitingQueue == null);

            time = Machine.timer().getTime();
            waitQueue.threadStates.add(this);
            waitingQueue = waitQueue;

            if(placement == 0)
                placement = placementInc++;

            if (waitQueue.owner != null && waitQueue.transferPriority)
            {
                waitQueue.owner.effectivePriority += getWinningPriority();
                waitQueue.owner.update();
            }

        }
        @Override
        public void acquire(PriorityQueue waitQueue) {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (waitQueue.owner != null)
                waitQueue.owner.ownedQueues.remove(waitQueue);

            waitQueue.owner = this;
            ownedQueues.add(waitQueue);

            Iterator<ThreadState> it =  waitQueue.threadStates.iterator();

            while(it.hasNext() && waitQueue.transferPriority)
            {
                ThreadState temp = it.next();
                effectivePriority += temp.getWinningPriority();
            }

            if (effectivePriority != effectivePriorityCopy)
                update();

        }

        @Override
        public void setPriority(int priority) {
            Lib.assertTrue(Machine.interrupt().disabled());
            if (this.priority == priority)
                return;

            this.priority = priority;
            recalculateThreadScheduling();
            update();
        }

        @Override
        public void update()
        {
            if (priorityCopy != priority)
            {
                effectivePriority = effectivePriority + (priority - priorityCopy);

                priorityCopy = priority;

                if (waitingQueue != null)
                    recalculateThreadScheduling();
            }

            if (waitingQueue == null || waitingQueue.owner == null)
            {
                effectivePriorityCopy = effectivePriority;
                return;
            }


            if (waitingQueue.transferPriority && effectivePriorityCopy != effectivePriority)
            {
                waitingQueue.owner.effectivePriority += (effectivePriority - effectivePriorityCopy);
                effectivePriorityCopy = effectivePriority;
                waitingQueue.owner.recalculateThreadScheduling();
                waitingQueue.owner.update();
            }
            else if (effectivePriority != effectivePriorityCopy)
                effectivePriorityCopy = effectivePriority;

        }

        // These copy values are made so as to check inside the update function of the threadstate
        // if there are necessary changes to be made.
        // For example, if priorityCopy value and priority value are the same than no longer recursive calls
        // need to be made. But if there is a difference then necessary steps need to be made to update the owner
        // of the queue.
        int priorityCopy;
        int effectivePriorityCopy;
    }
}
