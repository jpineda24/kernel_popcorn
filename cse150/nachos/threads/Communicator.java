// CURRENT COMMUNICATOR 

package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
	
    public Communicator() {
    	// Allocate variables
    	lock = new Lock();
    	speakerQueue = new Condition2(lock);
    	listenerQueue = new Condition2(lock);
		SL = new Condition2(lock);		//it makes sure that listener returns first
    	speakerReady = false;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	lock.acquire();
		speaker++;
    	// Check if there is a listener or speaker is ready
    	while(speakerReady){
    		speakerQueue.sleep();
		}
		// Save word
		this.message = word;
		//Set speaker flag to true
		speakerReady = true;
		//Broadcast
    	listenerQueue.wake();
		SL.sleep();
		speaker--;
		lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
		int word;
    	lock.acquire(); // acquire lock
		listener++;
				
    	// if speaker queue count greater than 0/not empty wake
    	// Else put listener to sleep
    	while(!speakerReady) {  // To be set in speaker
			listenerQueue.sleep();
    	}
    	speakerReady = false;
    	word = this.message;
		listener--;
    	speakerQueue.wake();
		SL.wake();
    	lock.release();  // release lock and return word
    	return word;
    }
    
    private Lock lock;
    private Condition2 speakerQueue;
    private Condition2 listenerQueue;
	private Condition2 SL;
    private int message;
	private int speaker = 0;
	private int listener = 0;
    // Condition variable that would be set in speaker and then tested in listener
    private boolean speakerReady;  
}


	