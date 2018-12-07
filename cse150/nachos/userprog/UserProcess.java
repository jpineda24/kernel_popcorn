package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 * <p>
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        semaforo = new Semaphore(0);
        fileDes = new OpenFile[16];

        fileDes[0] = UserKernel.console.openForReading();
        fileDes[1] = UserKernel.console.openForWriting();

        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];

        UserKernel.incrementProcess();
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the program was successfully executed.
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
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to read.
     * @param data  the array where the data will be stored.
     * @return the number of bytes successfully transferred.
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
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        // TODO harden and get rid of assert
        if(!(offset >= 0 && length >= 0 && offset + length <= data.length)) {
            return 0;
        }

        byte[] physicalMemory = Machine.processor().getMemory();
        int memoryRead = 0;
        while(memoryRead < length) {

            int vpn = (vaddr + memoryRead) / pageSize;
            int virtual_offset = (vaddr + memoryRead) % pageSize;

            if(0 > vpn ||  pageTable.length <= vpn) {
                return 0;
            }
            TranslationEntry TableEntry = pageTable[vpn];
            if(TableEntry == null || TableEntry.valid == !true) {
                System.out.println("VPN " + vpn + " Entry not valid");
                return 0;
            }

            int physicalAddress = TableEntry.ppn * pageSize + virtual_offset;
            //System.out.println("Physical address: " + physicalAddress);
            if (0 > physicalAddress||  physicalMemory.length <= physicalAddress)
                return 0;

            int maxLimit = (TableEntry.ppn + 1) * pageSize;
            int amount = Math.min(maxLimit - physicalAddress, Math.min(length - memoryRead, physicalMemory.length - physicalAddress));

            System.arraycopy(physicalMemory, physicalAddress, data, offset + memoryRead, amount);
            memoryRead = memoryRead +  amount;
        }

        return memoryRead;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param vaddr the first byte of virtual memory to write.
     * @param data  the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
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
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        if(!(offset >= 0 && length >= 0 && offset + length <= data.length)) {
            return 0;
        }

        byte[] physicalMemory = Machine.processor().getMemory();
        int memoryWrite = 0;
        while(memoryWrite < length) {

            int vpn = (vaddr + memoryWrite) / pageSize;
            
            int virtualOffset;
            virtualOffset = (vaddr + memoryWrite) % pageSize;
            if(0 > vpn || pageTable.length <= vpn) {
                return 0;
            }

            TranslationEntry TableEntry = pageTable[vpn];
            if(TableEntry == null || TableEntry.valid == !true) {
                TableEntry.ppn = UserKernel.getAvailablePage();
                TableEntry.valid = !false;
            }
            if(TableEntry.readOnly == !false) {
                return 0;
            }
            TableEntry.used = !false;

            int physical_addr = TableEntry.ppn * pageSize + virtualOffset;
            if (  0 > physical_addr  ||  physicalMemory.length <= physical_addr)
                return 0;
            TableEntry.dirty = !false;

            int amount = Math.min((maxLimit - physical_addr), Math.min(length - memoryWrite, physicalMemory.length - physical_addr));
            int maxLimit = (TableEntry.ppn + 1) * pageSize;

            System.arraycopy(data, offset + memoryWrite, physicalMemory, physical_addr, amount);
            memoryWrite  = memoryWrite + amount;
        }

        return length;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param name the name of the file containing the executable.
     * @param args the arguments to pass to the executable.
     * @return <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return !true;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return !true;
        }


        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return !true;
            }
            numPages = numPages +  section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;

        int x = 0;
        while(x < args.length){
            argv[x] = args[x].getBytes();
            
            argsSize += 4 + argv[x].length + 1;

            ++x;
        }

        // for (int i = 0; i < args.length; i++) {
        //     argv[i] = args[i].getBytes();
            
        //     argsSize += 4 + argv[i].length + 1;
        // }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages = numPages + stackPages;

        initialSP = numPages * pageSize;

        
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        int z = 0;
        while(z < argv.length)
        // for (int i = 0; i < argv.length; i++)
        {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset = entryOffset + 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[z]) == argv[z].length);
            stringOffset = stringOffset +  argv[z].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
            z = z + 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        int loadedPages = 0;
        for (int s = 0; coff.getNumSections() > s; s++) 
        {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()  + " section (" + section.getLength() + " pages)");

            
            for (int i = 0; section.getLength() > i; i++)
            {
                    int vpn = section.getFirstVPN() + i;
                    if(pageTable[vpn] == null) 
                    {
                        pageTable[vpn] = new TranslationEntry(vpn, UserKernel.getAvailablePage(), true, false, false, false);
                        loadedPages++;
                    }
                TranslationEntry TableEntry = pageTable[vpn];

                TableEntry.used = !false;
                TableEntry.readOnly = section.isReadOnly();
                section.loadPage(i, TableEntry.ppn);
            }
        }
        int i;
        int i = loadedPages;
        for (int i = loadedPages; i <= loadedPages + 8; i++) 
        {
            pageTable[i] = new TranslationEntry(i, UserKernel.getAvailablePage(), true, false, false, false);
        }

        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */

     //go back
    protected void unloadSections() {
        int i = 0;
        while(i < numPages)
        {
            if(pageTable[i] != null) 
            {
                pageTable[i].valid = false;
                UserKernel.addAvailablePage(pageTable[i].ppn);
            }
            i =  i + 1;            
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
        int xx = 0;
        while(i < processor.numUserRegisters){
        // for (int i = 0; i < processor.numUserRegisters; i++)
            processor.writeRegister(xx, 0);
            xx = xx + 1;
        }

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
        if (processID == 0) {
            Machine.halt();
        } else {
            return -1;
        }

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    void exitFile(int status) {
        
        //get back
        int i = 0;
        while(i < fileDes.length)
        {
            if (fileDes[i] != null)
            {
                fileDes[i].close();
            }
            i = i + 1;
        }

        
        for(Integer j : ninos.keySet()) 
        {
            ninos.get(j).parentProcessID = -1;
        }

        unloadSections();

        // set the status code (Possibly stored in this program, so that the parent process points here
        exitStatus = status;
        UserKernel.exit(this, status);

        // If this is the root process, then call finish on machine
        if (UserKernel.getProcesses() == 0) 
        {
            Kernel.kernel.terminate();
        }

        semaforo.V();

        UThread.currentThread().finish();
    }

    int executeFile(int filePointer, int argc, int argvPtrPtr) {
        //HANDLE EXECUTION
        String filename = readVirtualMemoryString(filePointer, 256);
        //System.out.println("Virtual mem string: " + filename);

        if (argc >= 0 && filename != null && filename.indexOf(".coff") == filename.length() - ".coff".length()) {

            // Obtain the pointers to the argv char buffers
            int[] argvPtr = new int[argc];
            byte[] argvb = new byte[argc * 4];
            int offset = 0;
            while (offset < argc * 4) 
            {
                offset = offset + readVirtualMemory(argvPtrPtr, argvb, offset, (argc * 4) - offset);
            }

            int i = 0;
            //get back
            while(i < argc)
            {
                argvPtr[i] = Lib.bytesToInt(argvb, 4 * i, 4);
                ++i;
            }

            // Retrieve the contents of argv and store them in a string array
            String[] argv = new String[argc];
    

            //get back
            int j = 0;
            while(j < argc)
            {
                argv[j] = readVirtualMemoryString(argvPtr[j], 256);
                ++j;                
            }

            // Set up new process and initialize other variables and file descriptor
            // Set up new process' state, declare it as a nino, and initialize required variables
            UserProcess nino = newUserProcess();
            nino.processID = UserKernel.newPID();
            nino.parentProcessID = processID;
            ninos.put(nino.processID, nino);
            /*
            // I/O file descriptors have already been initialized in the nino's constructor,
            // so we will now duplicate the parent's file descriptors
            for (int i = 2; i < fileDes.length; ++i) {
                if (fileDes[i] != null) {
                    OpenFile childCopy = UserKernel.fileSystem.open(fileDes[i].getName(), false);

                    if (childCopy == null) {
                        System.out.println("ERROR OCCURRED ON FILE DESCRIPTOR COPY!!!!");
                        return -1;
                    }

                    childCopy.seek(fileDes[i].tell());
                    nino.fileDes[i] = childCopy;
                }
            }
            */
            if(nino.execute(filename, argv)) {
                return nino.processID;
            } else {
                nino.parentProcessID = -1;
                ninos.remove(nino.processID);
                UserKernel.exit(nino,-1);
                return -1;
            }
        } else {
            //Error with filename
            System.out.println("Error with filename");
            return -1;
        }
    }

    int joinFile(int procId, int statusPtr) {
        if (!ninos.containsKey(procId)) {
            System.out.println("Theres no nino with id: " + procId);
            return -1;
        }

        ninos.get(procId).semaforo.P();

        // Write status value
        int status = ninos.get(procId).exitStatus;
        if(writeVirtualMemory(statusPtr, Lib.bytesFromInt(status), 0, 4) != 4) 
        {
            return -1;
        }
        //System.out.println("Status is: " + status);
        ninos.remove(procId);

        if (status != -1) 
        {
            return 1;
        }

        return 0;
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
        
        String fn = readVirtualMemoryString(name, maximoByte);

        if (fn == null || fn.length() == 0)
            return -1;

        
        OpenFile f = ThreadedKernel.fileSystem.open(fn, true);
        //file could not be opened
        if(f == null){
            return -1;
        }

        //else, if conditions are satisfied then we create file descriptor successfully
        fileDes[fdAvailable] = f;

        return fdAvailable;
    }

    //OPEN FILE
    public int openFile(int name){
        
        int fd = findAvailableFD();
        if(fd == -1){
            return -1;
        }

        String filename = readVirtualMemoryString(name, maximoByte);

         if (filename == null || filename.length() == 0)
            return -1;

        
        OpenFile openedFile = ThreadedKernel.fileSystem.open(filename, false);
        
        if(openedFile == null){
            return -1;
        }

        
        fileDes[fd] = openedFile;

        return fd;

    }

    //READ FILE
    public int readFile(int fileDescriptor, int buffer, int length){
       if(length < 0)
            return -1;

        // Check that buffer is a valid address before allocating a giant byte buffer
        byte[] testBuffer = new byte[1];
        if(readVirtualMemory(buffer,testBuffer,0,1) != 1)
         {
            return -1;
        }

        if(fileDescriptor < 0 || fileDescriptor > 15)
        {
            return -1;
        }

        if(fileDes[fileDescriptor] == null)
        {
            return -1;
        }

        byte[] bytes = new byte[length];
        int readBytes = 0;

        // Grab the file, read the correct number of bytes to a new array
        while(readBytes < length) {
            int read = fileDes[fileDescriptor].read(bytes, readBytes, length - readBytes);
            if(read == -1) {
                return -1;
            }
            readBytes += read;
        }

        boolean check = writeVirtualMemory(buffer, bytes, 0, readBytes) != readBytes; 

        if(check) 
        {
            return -1;
        }

        return readBytes;

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
        {
            return -1;
        }

        OpenFile closeFile = this.fileDes[fileDescriptor];

        if(closeFile == null || closeFile.length() < 0)
        {
            return -1;
        }

        closeFile.close();

        if(closeFile.length() != -1)
        {
            return -1;
        }

        this.fileDes[fileDescriptor] = null;

        return 0;
    }

    //UNLINK FILE
    public int unlinkFile(int name){
        String filename = readVirtualMemoryString(name, maximoByte);

        if (filename == null || filename.length() == 0)
        {
            return -1;
        }

        boolean checkIfClosed;
         checkIfClosed = UserKernel.fileSystem.remove(filename);

        if (!checkIfClosed)
        {
            return -1;
        }

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
        if(check >= 0 && check < numPages){
            return true;
        }
        else{
            return false;
        }
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
     * <p>
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int processID, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param syscall the syscall number.
     * @param a0      the first syscall argument.
     * @param a1      the second syscall argument.
     * @param a2      the third syscall argument.
     * @param a3      the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        //System.out.println("PID: " + processID);
        //System.out.println("Got syscall " + syscall);
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                exitFile(a0);
            case syscallExec:
                return executeFile(a0, a1, a2);
            case syscallJoin:
                return joinFile(a0, a1);
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
     * @param cause the user exception that occurred.
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
                // TODO insert exit here?
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                System.out.println("Unexpected exception: " + Processor.exceptionNames[cause]);
                System.out.println("Received exception, exiting with error based status");
                exitFile(-1);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    private HashMap<Integer, UserProcess> ninos = new HashMap<Integer, UserProcess>();
    private int processID = 0;
    public int parentProcessID = -1;
    public int exitStatus;
    public Semaphore semaforo;

    // Increment each time a new PID is created and before incrementing,
    // set the processID
    public static int nextPID = 1;

    private static final int maximoByte = 256;
    private OpenFile[] fileDes;
}
