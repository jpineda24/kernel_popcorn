package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.HashMap;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() { 
	    //at least 16 files will be supported concurrently
	    fileDes = new OpenFile [16];
	    semaforo = new Semaphore(0);
	
	    //initialize file descriptors 0 and 1 to 
	    fileDes[0] = UserKernel.console.openForReading();
	    fileDes[1] = UserKernel.console.openForWriting();
	
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i=0; i<numPhysPages; i++)
		    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	
	    //enable interrupts
        UserKernel.processes();
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */

     /**----------------------PART II: MULTIPROGRAMMING---------------------- */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) 
    {
        //get back
       if(!(offset >= 0 && length >= 0 && offset + length <= data.length)) {
            return 0;
        }

		byte[] physicalMemory = Machine.processor().getMemory();
		
		/**The memory is going to be read in bytes*/
		int memoryRead = 0;
		
		while(length > memoryRead)
		{
			int VPN = (memoryRead + vaddr) / pageSize;
			int virtualOffset = (memoryRead + vaddr) % pageSize;
			
			if(0 > VPN || pageTable.length <= VPN)
			{
				return 0;
			}
			
			TranslationEntry TableEntry = pageTable[VPN];
			
			if(TableEntry == null || TableEntry.valid == false)
			{
				System.out.println("VPN " + VPN + " entry not valid");
				return 0;
			}
			
			int physicalAddress = TableEntry.ppn * pageSize + virtualOffset;
			
			if(physicalAddress < 0 || physicalMemory.length <= physicalAddress)
			{
				return 0;
			}
			
			int maxLimit = (TableEntry.ppn + 1) * pageSize;
			int total = Math.min(maxLimit - physicalAddress, Math.min(length - memoryRead, physicalMemory.length - physicalAddress));
			
			System.arraycopy(physicalMemory, physicalAddress, data, offset + memoryRead, total);
			
			memoryRead = memoryRead + total;
		}
		
		return memoryRead;
		
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) 
    {
        //get back
         if(!(offset >= 0 && length >= 0 && offset + length <= data.length)) {
            return 0;
        }
	
		byte[] physicalMemory = Machine.processor().getMemory();
		
		int memoryWrite = 0;
		
		while(length > memoryWrite)
		{
			int VPN = (memoryWrite + vaddr) / pageSize;
			int virtualOffset = (memoryWrite + vaddr) % pageSize;
			
			if(0 > VPN || pageTable.length <= VPN)
			{
				return 0;
			}
			
			TranslationEntry TableEntry = pageTable[VPN];
			
			if(TableEntry == null || TableEntry.valid == false)
			{
				TableEntry.ppn = UserKernel.getAvailablePage();
				TableEntry.valid = !false;
			}
			
			if(TableEntry.readOnly == !false)
			{
				return 0;
			}
			
			TableEntry.used = !false;
			
			int physicalAddress = TableEntry.ppn * pageSize + virtualOffset;
			
			if(physicalAddress < 0 || physicalMemory.length <= physicalAddress)
			{
				return 0;
			}
			
			TableEntry.dirty = !false;
			
			int maxLimit = (TableEntry.ppn + 1) * pageSize;
			int total = Math.min(maxLimit - physicalAddress, Math.min(length - memoryWrite, physicalMemory.length - physicalAddress));
			
			System.arraycopy(data, offset + memoryWrite, physicalMemory, physicalAddress, total);
			
			memoryWrite = memoryWrite + total;
		}
		
		return length;
		

    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() 
    {
        if (numPages > Machine.processor().getNumPhysPages()) {
		    coff.close();
		    Lib.debug(dbgProcess, "\tinsufficient physical memory");
		    return false;
		}
	
        int pageLoad = 0;
        
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) 
		{
		    CoffSection section = coff.getSection(s);
		    
		    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
			      + " section (" + section.getLength() + " pages)");
	
		    for (int i = 0; i < section.getLength(); i++) 
		    {
				int vpn = section.getFirstVPN() + i;
				
				if(pageTable[vpn] == null)
				{
					pageTable[vpn] = new TranslationEntry(vpn, UserKernel.getAvailablePage(),true, false, false, false);
					pageLoad++;
				}
				
				TranslationEntry translateEntry = pageTable[vpn];
				
				translateEntry.used = !false;
				translateEntry.readOnly = section.isReadOnly();
				
				section.loadPage(i, translateEntry.ppn);
		    }
		}
		
		for(int i = pageLoad; i <= pageLoad + 8; i++)
		{
			pageTable[i] = new TranslationEntry(i, UserKernel.getAvailablePage(),true,false,false,false);
		}
		
		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() 
    {
        for(int i = 0; numPages > i; i++)
    	{
    		if(pageTable[i] != null)
    		{
    			pageTable[i].valid = !true;
    			UserKernel.addAvailablePage(pageTable[i].ppn);
    		}
    	}
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
        if(processID == 0)
        {
            Machine.halt();
        }
        else{
            return -1;
        }
    Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }


    //HANDLE EXIT
    void exitFile (int statusReport){
        int i;
        for(i = 0; fileDes.length > i; ++i){
            if(fileDes[i] != null){
                fileDes[i].close();
            }
        }

        for(Integer ii: ninos.keySet()){
            ninos.get(ii).parentProcessID = -1;
        }

        unloadSections();

        exitStatus = statusReport;
        UserKernel.exited(this, statusReport);

        if(0 == UserKernel.getProcesses()){
            Kernel.kernel.terminate();
        }

        semaforo.V();

        UThread.currentThread().finish();

    }

    int execution(int namePointer, int argument, int argumentPointer){
        String file = readVirtualMemoryString(namePointer, 256);

        if (argument >= 0 && file != null && file.indexOf(".coff") == file.length() - ".coff".length()) {

            // Obtain the pointers to the argv char buffers
            int[] arrayPtr = new int[argument];
            byte[] argvb = new byte[argument * 4];
            int offset = 0;
            while (offset < argument * 4) {
                offset += readVirtualMemory(argumentPointer, argvb, offset, (argument * 4) - offset);
            }
            for (int i = 0; i < argument; ++i) {
                arrayPtr[i] = Lib.bytesToInt(argvb, 4 * i, 4);
                //System.out.println("Argv pointer: " + arrayPtr[i]);
            }

            // Retrieve the contents of argv and store them in a string array
            String[] argv = new String[argument];
            for (int i = 0; i < argument; ++i) {
                argv[i] = readVirtualMemoryString(arrayPtr[i], 256);
                //System.out.println("Argv " + i + " " + argv[i]);
            }

            // Set up new process and initialize other variables and file descriptor
            // Set up new process' state, declare it as a nino, and initialize required variables
            UserProcess nino = newUserProcess();
            nino.processID = UserKernel.newProcessID();
            nino.parentProcessID = processID;
            ninos.put(nino.processID, nino);
            
            if(nino.execute(file, argv)) {
                return nino.processID;
            } else {
                nino.parentProcessID = -1;
                ninos.remove(nino.processID);
                UserKernel.exited(nino,-1);
                return -1;
            }
        } else {
            //Error with file
            System.out.println("Error with file");
            return -1;
        }
    }

    int joining(int procId, int statusPtr) {
        if (!ninos.containsKey(procId)) {
            System.out.println("Theres no child with id: " + procId);
            return -1;
        }

        ninos.get(procId).semaforo.P();
    

        // Write status value
        int status = ninos.get(procId).exitStatus;
        if(writeVirtualMemory(statusPtr, Lib.bytesFromInt(status), 0, 4) != 4) {
            return -1;
        }
        //System.out.println("Status is: " + status);
        ninos.remove(procId);

        if (status != -1) {
            return 1;
        }

        return 0;
    }

    private int creat(int nameAddr) {

        // Find open file descriptor and assign
        // Translate the memory address of the filename string
        // Open file with the provided StubFileSystem passing create flag (true)
        // Return assigned file descriptor

        int fileDescriptor = -1;

        for (int i = 0; i < this.fileDes.length; i++)
            if (fileDes[i] == null)
                fileDescriptor = i;

        if (fileDescriptor == -1)
            return -1;

        String filename = readVirtualMemoryString(nameAddr, MAX_FILENAME_BYTE_SIZE);

        if (filename == null || filename.length() == 0)
            return -1;

        OpenFile newFile;

        newFile = UserKernel.fileSystem.open(filename, true);

        if (newFile == null)
            return -1;

        this.fileDes[fileDescriptor] = newFile;

        return fileDescriptor;
    }

    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int processID, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExit:
        exitFile(a0);
    case syscallExec:
        return execution(a0, a1, a2);
    case syscallJoin:
        return joining(a0, a1);
    case syscallCreate:
		return createFile(a0);
    case syscallOpen:
        return openFile(a0);
    case syscallRead:
        return readFile(a0, a1, a2);
    case syscallWrite:
        return writeFile(a0, a1, a2);
    case syscallClose:
        return closeFile(a0);
    case syscallUnlink:
        return unlinkFile(a0);


	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }


//----------------------------------------------------------------------------------------------------------------------------------------------------------
    //PART I: 6 SYSTEM CALLS (creat, open, read, write, close, and unlink)


    //CREATE FILE
    public int createFile(int name){
        //check if there are any file descriptors that are currently available
        int fdAvailable = findAvailableFD();
        if(fdAvailable == -1){
            return -1;
        }
        
        String filename = readVirtualMemoryString(name, MAX_FILENAME_BYTE_SIZE);

        if (filename == null || filename.length() == 0)
            return -1;

        //create a file. otherwise false
        OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
        //file could not be opened
        if(file == null){
            return -1;
        }

        //else, if conditions are satisfied then we create file descriptor successfully
        fileDes[fdAvailable] = file;

        return fdAvailable;
    }

    //OPEN FILE
    public int openFile(int name){
        //check to see if file to be opened exists
        int fileExists = findAvailableFD();
        if(fileExists == -1){
            return -1;
        }

        String filename = readVirtualMemoryString(name, MAX_FILENAME_BYTE_SIZE);

         if (filename == null || filename.length() == 0)
            return -1;

        //attempt opening named file
        OpenFile openedFile = ThreadedKernel.fileSystem.open(filename, false);
        //if file cannot be opened at the moment, return -1
        if(openedFile == null){
            return -1;
        }

        //if both of the conditions above are satisfied, then return the opened file descriptor
        fileDes[fileExists] = openedFile;

        return fileExists;

    }

    //READ FILE
    public int readFile(int fileDescriptor, int buffer, int length){
       if(length < 0)
            return -1;

        // Check that buffer is a valid address before allocating a giant byte buffer
        byte[] addrTestBuff = new byte[1];
        if(readVirtualMemory(buffer,addrTestBuff,0,1) != 1) {
            return -1;
        }

        if(fileDescriptor < 0 || fileDescriptor > 15)
            return -1;

        if(fileDes[fileDescriptor] == null)
            return -1;

        byte[] bytes = new byte[length];
        int bytesRead = 0;

        // Grab the file, read the correct number of bytes to a new array
        while(bytesRead < length) {
            int read = fileDes[fileDescriptor].read(bytes, bytesRead, length - bytesRead);
            if(read == -1) {
                return -1;
            }
            bytesRead += read;
        }


        // Write the read bytes to virtual memory
        if(writeVirtualMemory(buffer, bytes, 0, bytesRead) != bytesRead) {
            return -1;
        }

        return bytesRead;

    }

    //WRITE INTO FILE
    public int writeFile(int fileDescriptor, int buffer, int length){
        //if either of these is not true then error happens
       if(checkInvalid(fileDescriptor) || inBounds(buffer) || length < 0){
           UThread.currentThread().finish();
           return -1;
       }

       byte buff [] = new byte[length];
       OpenFile writeF = fileDes[fileDescriptor];

      // int bytesWritten = readVirtualMemory.read(buffer, buff);
       int bytesWritten = readVirtualMemory(buffer,buff);
       
       if(bytesWritten != length){
           return -1;
       }

       //try writing bytes
       bytesWritten = writeF.write(buff, 0, length);
       //if faiulure to reda then return -1
       if(bytesWritten == -1){
           return -1;
       }

       return bytesWritten;
   }

    //CLOSE FILE
    public int closeFile(int fileDescriptor){
        if(fileDescriptor < 0 || fileDescriptor > 15)
            return -1;

        OpenFile fileToClose = this.fileDes[fileDescriptor];

        if(fileToClose == null || fileToClose.length() < 0)
            return -1;

        fileToClose.close();

        if(fileToClose.length() != -1)
            return -1;

        this.fileDes[fileDescriptor] = null;

        return 0;
    }

    //UNLINK FILE
    public int unlinkFile(int name){
        String filename = readVirtualMemoryString(name, MAX_FILENAME_BYTE_SIZE);

        if (filename == null || filename.length() == 0)
            return -1;

        boolean isClosed = UserKernel.fileSystem.remove(filename);

        if (!isClosed)
            return -1;

        return 0;
    }

/////////HELPER FUNCTIONS/////////

    //check if file descriptor is invalid
    public boolean checkInvalid(int fd){
        if(fileDes[fd] == null){
            return false;
        }
        else{
            return true;
        }
    }

    //return avilable file descriptor
    public int findAvailableFD(){
        int i;
        for(i = 0; i < 16; i++){
            if(fileDes[i] == null){
                return i;
            }
        }
        return -1;
    }

    //find file descriptor by name
    public int getFDCreated(String name){
        for(int i = 0; i < 16; i++){
            if(name == fileDes[i].getName()){
                return i;
            }
        }
        return -1;
    }

    //check if address is within bounds
    boolean inBounds(int buffer){
        int check = Processor.pageFromAddress(buffer);
        if(check >= 0 && check < pageNumber){
            return true;
        }
        else{
            return false;
        }
    }

    /**this will get the entry from the page table */
//    private TranslationEntry getPageTableEntry(int VPN)
//    {
//    	if(pageTable.length == 0 || !inBounds(VPN))
//    	{
//    		return null;
//    	}
//    	
//    	return pageTable[VPN];
//    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv; 
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    /**NEWLY CREATED VARIABLES**/

    private HashMap<Integer, UserProcess> ninos = new HashMap<Integer, UserProcess>();
    public int parentProcessID = -1;
    public int exitStatus;


    //crete file descriptor array
    protected OpenFile fileDes[];

    //page number
    protected int pageNumber = 0;

    private int processID = 0; 
    protected UThread userThread;

    public Semaphore semaforo;

     private static final int MAX_FILENAME_BYTE_SIZE = 256;
    private static final int MAX_OPEN_FILES = 16;

}
