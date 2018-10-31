package nachos.threads;
import java.util.*; 
import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
	 ///////////////////////////////////PRIORITY_QUEUE	////////////////////////////////////
    protected class PriorityQueue extends ThreadQueue {
    
    	// Variables For PriorityQueue
    ThreadState current;	// current thread
    LinkedList <ThreadState> tstate;	// Linked list of thread states
    boolean flag;	// Flag for checking priority update
    int effectiveP;
	boolean transfer;
    
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;

	    // Allocate variables
	    current = null;
	    tstate = new LinkedList <ThreadState>();
	    flag = false;
		effectiveP = 0;
		transfer = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	    
	    //Get threadstate of argument and add to List of threadstates
	    tstate.add(getThreadState(thread));
	    
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	    // sets current thread to thread state
		//if old owner is not null then remove it from queue
	    if(current != null){
			current.currentResources.remove(this);
			current.setCacheToDirty();
		}
		//acquire resource and create current ownser of thread
	   ThreadState nextTstate = getThreadState(thread);
	   current = nextTstate;
	   nextTstate.acquire(this);
	}

	// Select next thread in queue.
	public KThread nextThread() {
		Lib.assertTrue(Machine.interrupt().disabled());


		if(tstate.isEmpty()) {	// Check to see if thread list is empty
	    	return null;
		}
		
		// Call to pickNextThread that will return highest priority thread
		ThreadState nextThread = pickNextThread();
	
		
	    // Check nextThread if not null, remove from LinkedList tstate and acquire resource
	    if (nextThread != null) {	
	    	tstate.remove(nextThread);
	    	acquire(nextThread.thread);	// current acquires resource
	    }
	    
	   
	    return nextThread.thread;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {	// Returns KThread instead of ThreadState
	    // implement me
		ThreadState nextTS = null;

		if(!tstate.isEmpty()){	
			//Get first threadstate from list
			nextTS = tstate.getFirst();
			//Loop through list and Check Priorities
			
			for(int i = 0; i < tstate.size(); i++) {
				ThreadState temp = tstate.get(i);	// Set temp to threadstates in LinkedList tlist
							
				if(temp.getEffectivePriority() > nextTS.priority ) {	// Compare priorities and set to thread for return.  
					nextTS = temp;	//nextTS gets largest priority
				}
				
			}
			
		}
		
		return nextTS;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	}

	/*PriorityQueue getEffectivePriority() will loop through  the list of 
	 * threadstates and return its highest priority.
	 * To be called in pickNextThread
	 * If queue is flagged, return max priority of each thread.
	 */
	int getEffectivePriority() {
		// Check if thread has been flagged for update
		if(flag && transferPriority) { 
			effectiveP = priorityMinimum;
			flag = false;
			
			// Loop through LinkedList 
			for(int i = 0; i < tstate.size();i++) {
				ThreadState temp = tstate.get(i);
				//Compare priorities and set effective to largest
				if (temp.getEffectivePriority() > effectiveP) {
					effectiveP = temp.getEffectivePriority();
				}
			}
		}
		
		return effectiveP;
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
	//////////////////////////////////////////////////THREADSTATE /////////////////////////////////////////////////////////
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	 //list for resources this thread is waiting on
	 LinkedList<PriorityQueue> waitingOnResources;
	 //list for resources this thread is currently holding
	 LinkedList<PriorityQueue> currentResources;
	//Effective priority
	int effectiveP = 0;
	//dirty bit
	boolean dirtyBit = false;


	public ThreadState(KThread thread) {
	    this.thread = thread;
		waitingOnResources = new LinkedList<PriorityQueue>();
		currentResources = new LinkedList<PriorityQueue>();
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    // check if no resources held in the thread
		if(currentResources.isEmpty())
	    	return priority;
		//otherwise get the effective priority of the resources currently being held1
		if(dirtyBit){
			this.effectiveP = priority;
			//no updates needed after this one
			dirtyBit = false;
			for(int i = 0; i < currentResources.size(); i++){
				int currentPriority = currentResources.get(i).getEffectivePriority();
				if(currentPriority > effectiveP){
					effectiveP = currentPriority;
				}
			}	
		}
		return effectiveP; 
	}


	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    
	   for(int i = 0; i < waitingOnResources.size(); i++){ 
			waitingOnResources.get(i).flag = true;
		}
	}

	//Helper function to set queue to dirty when priority has changed
	public void setCacheToDirty(){
		if(!dirtyBit){
			dirtyBit = true;
		}
		for(int i = 0; i < waitingOnResources.size(); i++){ 
			waitingOnResources.get(i).flag = true;
		}
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    //waiting for these resources
		waitingOnResources.add(waitQueue);
		//delete from current resources if in there
		currentResources.remove(waitQueue);

		//let priorityqueue know about update
		waitQueue.flag = true;
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    //delete resource queue from waiting for access list
		waitingOnResources.remove(waitQueue); 
		//obtain resoure queue queue
		currentResources.add(waitQueue);

		//let priorityqueue know about update
		waitQueue.flag = true;
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
    }
}

