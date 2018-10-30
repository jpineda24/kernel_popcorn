package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.threads.KThread;

public class Boat
{
    static BoatGrader bg;
    
    private static Communicator theCommunicator;
    private static Lock lockOfBoat;
    private static String locationOfBoat;

    static private int adultTotal;
    static private int adultAtOahu;
    static private int adultAtMolokai;

    static private int childTotal;
    static private int childAtOahu;
    static private int childAtMolokai;
    
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
	System.out.println("\n ***Testing Boats with only 2 children***");
	begin(0, 2, b);

	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
  	begin(1, 2, b);

  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
  	begin(3, 3, b);
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Instantiate global variables here
        theCommunicator = new Communicator();
        lockOfBoat = new Lock();
        locationOfBoat = "Oahu";

        /*The number of total adults and children are being initialized*/
        adultTotal = adults;
        childTotal = children;

        adultAtOahu = adults;
        childAtOahu = children;
        
        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.

        /*the adult threads are initialized */
        Runnable adultRunnable = new Runnable()
        {
            public void run() {
                AdultItinerary();
            }
        };
        for(int i = 0; i < adultTotal; i++)
        {
            KThread adultThread = new KThread(adultRunnable);
            adultThread.setName("Adult " + i);
            adultThread.fork();
        }


        /* the child threads are initialized*/
        Runnable childRunnable = new Runnable()
        {
            public void run()
            {
                ChildItinerary();
            }
        };
        for(int j = 0; j < childTotal; j++)
        {
            KThread childThread = new KThread(childRunnable);
            childThread.setName("Child " + j);
            childThread.fork();
        }


        /** the communicator is checking if there are still threads at Oahu */
        while(theCommunicator.listen() != (adultTotal + childTotal))
        {
            if(theCommunicator.listen() == (adultTotal + childTota))
            {
                break;
            }
        }
    }

    static void AdultItinerary()
    {
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
    */

        /**we can tell the communicator that everyone is at Malokai if the problem has been solved */
        while(true)
        {
            if(adultTotal == adultAtMolokai && childTotal == childAtMolokai)
            {
                theCommunicator.speak(adultTotal + childTotal);
                break;
            }
        }

        /**this is for when the boat is at Oahu */
        if(locationOfBoat.equals("Oahu"))
        {
            /**checking if there are is at least one child and adult at Oahu*/
            if(adultAtOahu >= 1 && (childTotal != childAtOahu))
            {
                lockOfBoat.acquire();       //checking no one can row besides us

                bg.AdultRowToMolokai();

                /**as the adults move the values of the adults for each location will change respectively */
                adultAtMolokai = adultAtMolokai + 1;
                adultAtOahu = adultAtOahu - 1;
                locationOfBoat = "Molokai";     //the boat is at molokai after moving adult

                /**if there are children left at Oahu, a child will be sent to pick the others
                 * THIS WILL ALWAYS BE THE SITUATION
                 */
                if(childTotal != childAtMolokai)
                {
                    bg.ChildRowToOahu();    //one child will be sent to pick up the rest of the children

                    /**as the child move the values of the children for each location will change respectively */
                childAtMolokai = adultAtMolokai - 1;
                adultAtOahu = adultAtOahu + 1;
                locationOfBoat = "Oahu";     //the boat is at oahu
                }

                lockOfBoat.release();       //the held lock will be released
            }
        }

        KThread.yield();
    }

    static void ChildItinerary()
    {

        while(true)
        {
            /**we can tell the communicator that everyone is at Malokai if the problem has been solved */
            if(adultTotal == adultAtMolokai && childTotal == childAtMolokai)
            {
                theCommunicator.speak(adultTotal + childTotal);
                break;
            }

            if(locationOfBoat.equals("Oahu"))
            {
                /**if there are no more adults at Oahu, the children will be the only ones going to Molokai */
                if(adultAtOahu == 0)
                {
                    /**while there are children at Oahu */
                    while(childAtOahu > 0)
                    {
                        /**if there is only one child left, have it go to Molokai on its own */
                        if(childAtOahu == 1)
                        {
                            bg.childRowToMolokai();

                            childAtOahu = childAtOahu - 1;
                            childAtMolokai = childAtMolokai + 1;
                            locationOfBoat = "Molokai";
                        }
                        /**
                         * if there are more children, we will be having 2 people row to molokai
                         * and have 1 sent back to Oahu to get another child
                         */
                        else
                        {
                            bg.childRowToMolokai();
                            bg.ChildRideToMolokai();

                            /**amount children will change depending on the location */
                            childAtMolokai = childAtMolokai + 2;
                            childAtOahu = childAtOahu - 2;
                            locationOfBoat = "Molokai";

                            childAtMolokai = childAtMolokai - 1;
                            childAtOahu = childAtOahu + 1;
                            locationOfBoat = "Oahu";
                        }
                    }
                    break;
                }

                /**this will take care for when there at least 2 children left at Oahu */
                if(childAtOahu >= 2)
                {
                    lockOfBoat.acquire();

                    /**it will be sending 2 children to Molokai */
                    bg.childRowToMolokai();
                    bg.ChildRideToMolokai();

                    /**the amount of children will change for each place */
                    childAtOahu = childAtOahu - 2;
                    childAtMolokai = childAtMolokai + 2;
                    locationOfBoat = "Molokai";

                    /**this will check if there are any adults left at Oahu to have 1 child go get them */
                    if(adultTotal != adultAtMolokai)
                    {
                        bg.ChildRowToOahu();

                        childAtOahu = childAtOahu + 1;
                        childAtMolokai = childAtMolokai - 1;
                        locationOfBoat = "Oahu";
                    }
                    lockOfBoat.release();
                }
            }
            else if(locationOfBoat.equals("Molokai"))
            {
                lockOfBoat.acquire();

                /**this scenario is for when all adults are at Molokai */
                if(adultTotal > 0)
                {
                    if(adultTotal == adultAtMolokai)
                    {
                        /**this will focus on only moving the children */
                        while(childTotal < childAtMolokai)
                        {
                            bg.ChildRowToOahu();

                            childAtMolokai = childAtMolokai - 1;
                            childAtOahu = childAtOahu + 1;

                            bg.ChildRowToMolokai();
                            bg.ChildRideToMolokai();

                            childAtMolokai = childAtMolokai + 2;
                            childAtOahu = childAtOahu - 2;
                        }
                        /**theCommunicator will know when everyone got to Molokai */
                        theCommunicator.speak(adultTotal + childTotal);
                    }
                }

                /**case for when there is one child back at Oahu, we only need
                 * to send one child to pick up the other
                 */
                if(childTotal != childAtMolokai)
                {
                    bg.ChildRowToOahu();

                    childAtOahu = childAtOahu + 1;
                    childAtMolokai = childAtMolokai - 1;

                    bg.ChildRowToMolokai();
                    bg.ChildRideToMolokai();

                    childAtOahu = childAtOahu - 2;
                    childAtMolokai = childAtMolokai + 2;
                }

                lockOfBoat.release();
            }
            KThread.yield();        //one must yield if the conditions weren't met
        }
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
}
