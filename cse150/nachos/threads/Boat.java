package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	static BoatGrader bg;
	
    static Communicator theCommunicator;
    static Lock lockOfBoat;
    static String locationOfBoat;

	static  int adultTotal;
	static  int childTotal;
	static  int adultsAtOahu;
	static  int childAtOahu;
	static  int adultsAtMolokai;
	static  int childAtMolokai;

	public static void begin( int adults, int children, BoatGrader b )
	{
		bg = b;

		/**the global variables will be initiated inside the begin() funstion */
		theCommunicator = new Communicator();
		lockOfBoat = new Lock();
		locationOfBoat = "OahuLocation";

		/** the variables for the total amount of children and adults will be initiated with the inputted values of the function */
		adultTotal = adults;
		childTotal = children;

		adultsAtOahu = adults;
		childAtOahu = children;

		/** the following statements will be initializing the adult and child threads */
		Runnable adultRunnable = new Runnable()
		{
			public void run()
			{
				AdultItinerary();
			}
		};
        int x = 0;
		while (x < adultTotal)
		{
			KThread adultThread = new KThread(adultRunnable);
			adultThread.fork();
            x = x + 1;
		}

		Runnable childRunnable = new Runnable()
		{
			public void run()
			{
				ChildItinerary();
			}
		};
        int i = 0;
		while(i < childTotal)
		{
			KThread childThread = new KThread(childRunnable);
			childThread.fork();
            i = i + 1;
		}

		/** the communicator used in the while look will be searching if there are any adult and child threads at Oahu */
		while (theCommunicator.listen() != (adultTotal + childTotal))
		{
			if (theCommunicator.listen() == (adultTotal + childTotal))
			{
				break;
			}
		}
	}

	static void AdultItinerary()
	{
		while (!false)
		{
			/** this 'if' statement is checking to see if the problem has been solved so it can communicate
			it to the communicator that everyone has arrived to Molokai */
			if (adultTotal == adultsAtMolokai && childTotal == childAtMolokai)
			{
				int info = adultTotal + childTotal;
				theCommunicator.speak(info);

				break;
			}

			/**this 'if' statement is checking for when the boat is at Oahu */
			if (locationOfBoat.equals("OahuLocation"))
			{
				/**the 'if' statement is checking if there is at least a child left at Oahu
				and if there is only one adult left at Oahu */
				if ((childTotal != childAtOahu) && (1 < adultsAtOahu || adultsAtOahu == 1))
				{
					/** the lock of the boat is making sure that no one else can be able to row beside us */
					lockOfBoat.acquire();

					/** the adults will be rowing to Molokai */
					bg.AdultRowToMolokai();

					/** the amount of adults will be changing depending where they are going.
						This is having the adults going from Oahu to Molokai*/
					adultsAtOahu = adultsAtOahu - 1;
					adultsAtMolokai = adultsAtMolokai + 1;

					/** as a result the boat ends up at Molokai */
					locationOfBoat = "MolokaiLocation";


					/** this 'if' statement is checking to see if children are still left at Oahu
						so they will be sending another child to pick them up */
					if (childTotal != childAtMolokai)
					{
						/** the one child is sent to Oahu to pick up the rest of the children */
						bg.ChildRowToOahu();

						/** the amount of children will be changing depending where they are going.
						This is having the children going from Molokai to Oahu*/
						childAtOahu = childAtOahu + 1;
						childAtMolokai = childAtMolokai - 1;

						/** as a result the boat ends up at Oahu */
						locationOfBoat = "OahuLocation";
					}

					/** the lock that has been held will be released */
					lockOfBoat.release();
				}
			}

			KThread.yield();
		}
	}

	static void ChildItinerary()
	{
		//DO NOT PUT ANYTHING ABOVE THIS LINE.

		while (!false)
		{
			/** this 'if' statement is checking to see if the problem has been solved so it can communicate
				it to the communicator that everyone has arrived to Molokai */
			if (adultTotal == adultsAtMolokai && childTotal == childAtMolokai)
			{
				int info1 = adultTotal + childTotal;
				theCommunicator.speak(info1);

				break;
			}

			/**this 'if' statement is checking for when the boat is at Oahu */
			if (locationOfBoat.equals("OahuLocation"))
			{
				/** if there aren't any adults left at Oahu we can now focus on bringing
					the children to Molokai */
				if (adultsAtOahu == 0) 
				{
					/** we will be performing the following statements while there are
						children still left at Oahu */
					while (childAtOahu > 0)
					{
						/** when there is only 1 child left at Oahu that 1 child will go
							to Molokai alone */
						if (childAtOahu == 1)
						{
							bg.ChildRowToMolokai();

							childAtOahu = childAtOahu - 1;
							childAtMolokai = childAtMolokai + 1;

							locationOfBoat = "MolokaiLocation";
						}
						/** the 'else' statement happens there is more than one child still at Oahu,
							the system will be having two children going to Molokai and sending one
							back to Oahu */
						else
						{
							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();

							childAtOahu = childAtOahu - 2;
							childAtMolokai = childAtMolokai + 2;

							locationOfBoat = "MolokaiLocation";

							bg.ChildRowToOahu();

							childAtOahu = childAtOahu + 1;
							childAtMolokai = childAtMolokai - 1;

							locationOfBoat = "OahuLocation";
						}
					}

					break;
				}

				/** the 'if' statement is for when there is at least two children
					left at Oahu */
				if (2 <= childAtOahu || childAtOahu > 2)
				{
					lockOfBoat.acquire();

					/** the boat will be traveling to Molokai */
					bg.ChildRowToMolokai();
					bg.ChildRideToMolokai();

					/** the amount of children will be changing depending where they are going.
						This is having the children going from Oahu to Molokai*/
					childAtOahu = childAtOahu - 2;
					childAtMolokai = childAtMolokai + 2;

					locationOfBoat = "MolokaiLocation";

					/** this will check if for some reason there is an adult left at Oahu,
						we will have one child go on the boat to Oahu */
					if (adultTotal != adultsAtMolokai)
					{
						bg.ChildRowToOahu();

						childAtOahu = childAtOahu + 1;
						childAtMolokai = childAtMolokai - 1;

						locationOfBoat = "OahuLocation";
					}

					lockOfBoat.release();
				}
			}
			/**this 'if' statement is checking for when the boat is at Molokai */
			else if (locationOfBoat.equals("MolokaiLocation"))
			{
				lockOfBoat.acquire();

				/** when all of the adults are at Molokai */
				if (adultTotal > 0)
				{

					/** this happens when all the adults are at Molokai */
					if (adultTotal == adultsAtMolokai)
					{
						/** we pick up the rest of the children by having one child
							go from Molokai to Oahu */
						while (childAtMolokai > childTotal)
						{
							bg.ChildRowToOahu();

							childAtMolokai = childAtMolokai - 1;
							childAtOahu = childAtOahu + 1;

							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();

							childAtMolokai = childAtMolokai + 2;
							childAtOahu = childAtOahu - 2;
						}

						/** once all the children are at Molokai we will use the 
							communicator to signal that everyone is at Molokai */
						int info = adultTotal + childTotal;
						theCommunicator.speak(info);
					}
				}

				/** this checks if there is at least one child at Oahu to 
					have another child go and pick them up */
				if (childTotal != childAtMolokai)
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

			/** when the contidition are not met we must yield the thread */
			KThread.yield();
		}
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		// System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}