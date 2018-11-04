package nachos.threads;

import java.util.Comparator;
import java.util.PriorityQueue;
import nachos.machine.*;


/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm 
{

    //create waitingQueue of WaitingThreads, ordered through TimeComparator
    PriorityQueue<ThreadWait> waitingQueue; //initial capacity, order WaitingComparator

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm(){
        waitingQueue = new PriorityQueue<ThreadWait>(10, new TimeCompare());
        Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() 
    {
	
        Machine.interrupt().disable(); //disable

        //if waitingQueue is empty, and current time is greater than or equal to the first ThreadWaits, wakeUp time,
        while(!waitingQueue.isEmpty() && (waitingQueue.peek().wakeUp < Machine.timer().getTime()
            || waitingQueue.peek().wakeUp ==  Machine.timer().getTime())) {
            waitingQueue.poll().thread.ready(); //pop head
        }

        KThread.currentThread().yield();

        Machine.interrupt().enable(); //enable

    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x){

        Machine.interrupt().disable(); //disable interrupts

	    long wakeTime; 
        wakeTime =  Machine.timer().getTime() ;
        wakeTime = wakeTime + x; //calculate wakeTime

        //pass through wakeTime and current thread as instance variables for a
        ThreadWait a;
        a = new ThreadWait(wakeTime, KThread.currentThread());

        waitingQueue.add(a); //add a to the waitingQueue 

        KThread.currentThread().sleep(); //sleep current thread

        Machine.interrupt().enable(); //enable interrupts
    }

    public class TimeCompare implements Comparator<ThreadWait>{ //for comparing wait times
    
        public int compare(ThreadWait a, ThreadWait b)
        {
            if(b.wakeUp > a.wakeUp){
                return -1;
            }
            if (b.wakeUp == a.wakeUp){
                return 0;
            }
           
            return 1;
        
        }
    }


    //PriorityQueue of waiting threads
    public class ThreadWait{
        long wakeUp; //time to wake up;
        KThread thread; //thread associated with

        //initialize with these variables
        ThreadWait(long timeTowakeUp, KThread threadS){
            wakeUp = timeTowakeUp;
            thread = threadS;
        }

    }
}
