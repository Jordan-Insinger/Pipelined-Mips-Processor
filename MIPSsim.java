// ON MY HONOR I HAVE NEITHER GIVEN NOR RECEIVED UNAUTHORIZED AID ON THIS ASSIGNMENT //

import java.io.*;
import java.util.*;

class Pair {
    MIPSsim instruction;
    Integer result;

    MIPSsim getKey() {
        return instruction;
    }

    Integer getValue() {
        return result;
    }

    Pair() {
        instruction = null;
        result = null;
    }

    Pair(MIPSsim I, Integer r) {
        instruction = I;
        result = r;
    }
}

class storageLocation {
    // =============== STORAGE LOCATIONS ============= //
    int PC = 256;
    Map<Integer, Integer> memoryAddresses = new HashMap<>();
    ArrayList<Integer> registers = new ArrayList<>();
    ArrayList<Integer> validRegisters = new ArrayList<>();
    Queue<MIPSsim> PreIssue_Queue = new LinkedList<>();
    Queue<MIPSsim> PreALU1_Queue = new LinkedList<>();
    Queue<MIPSsim> PreALU2_Queue = new LinkedList<>();
    Pair PostALU2_Queue = new Pair();
    Pair PostMEM_Queue = new Pair();
    Queue<MIPSsim> PreMEM_Queue = new LinkedList<>();
    MIPSsim IFwaiting = null;
    MIPSsim IFexecuted = null;
}

class functionalUnit {
    boolean checkBranchRegisters(storageLocation storage, MIPSsim instruction) {
        if (Arrays.asList("BGTZ", "BLTZ", "JR").contains(instruction.Opcode)) { // These 3 mips instructions have dependency on only source 1
            if (storage.validRegisters.get(instruction.source1) == 1) { // first check if the source1 bit is already marked invalid
                for (MIPSsim tempInstruction : storage.PreIssue_Queue) {
                    if (!tempInstruction.Opcode.equals("SW")) {
                        if (tempInstruction.destination == instruction.source1)
                            return false;
                    }
                }
                return true;
            } else return false;  // source1 bit is already invalid... waiting for write-back
        } else if (instruction.Opcode.equals("J")) return true;

        else {
            if (storage.validRegisters.get(instruction.source1) == 1 &&
                    storage.validRegisters.get(instruction.source2) == 1) { // checking sources 1 and 2
                for (MIPSsim tempInstruction : storage.PreIssue_Queue) {
                    if (!tempInstruction.Opcode.equals("SW")) {
                        if (tempInstruction.destination == instruction.source1)
                            return false;
                    }
                }
                return true;
            } else return false;  // source1 or source2 bit is already invalid... waiting for write-back

        }
    }

    boolean checkInstructionRegisters(storageLocation storage, MIPSsim instruction) {
        if (Arrays.asList("ADD", "SUB", "MUL", "AND", "OR", "XOR", "NOR", "SLT").contains(instruction.Opcode)) {
            return storage.validRegisters.get(instruction.source1) == 1 && storage.validRegisters.get(instruction.source2) == 1;
        } else if (Arrays.asList("ADDI", "ANDI", "ORI", "XORI", "SLL", "SRL", "SRA").contains(instruction.Opcode)) {
            return storage.validRegisters.get(instruction.source1) == 1;
        }
        // LW / SW Instruction
        else {
            return storage.validRegisters.get(instruction.destination) == 1 && storage.validRegisters.get(instruction.base) == 1;
        }
    }

    boolean checkPreIssueQueueRegisters(MIPSsim currentInstruction, MIPSsim preIssue_instruction) {
        // LW and SW

        // Regular Instructions
        if (Arrays.asList("ADD", "SUB", "MUL", "AND", "OR", "XOR", "NOR", "SLT").contains(currentInstruction.Opcode)) {
            if (Arrays.asList("ADD", "SUB", "MUL", "AND", "OR", "XOR", "NOR", "SLT").contains(preIssue_instruction.Opcode)) {
                if (!preIssue_instruction.issued) {
                    return (currentInstruction.source1 != preIssue_instruction.destination && currentInstruction.source2 != preIssue_instruction.destination) // RAW, WAW, WAR
                            && (currentInstruction.destination != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.source1 && currentInstruction.destination != preIssue_instruction.source2);
                } else {  // previous instruction is issued, don't check for WAR
                    return (currentInstruction.source1 != preIssue_instruction.destination && currentInstruction.source2 != preIssue_instruction.destination) // RAW, WAW
                            && (currentInstruction.destination != preIssue_instruction.destination);
                }
            } else if (Arrays.asList("ADDI", "ANDI", "ORI", "XORI", "SLL", "SRL", "SRA").contains(preIssue_instruction.Opcode)) {
                if (!preIssue_instruction.issued) {
                    return (currentInstruction.source1 != preIssue_instruction.destination && currentInstruction.source2 != preIssue_instruction.destination) // RAW, WAW, WAR
                            && (currentInstruction.destination != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.source1);
                } else {  // previous instruction is issued, don't check for WAR
                    return (currentInstruction.source1 != preIssue_instruction.destination && currentInstruction.source2 != preIssue_instruction.destination) // RAW, WAW
                            && (currentInstruction.destination != preIssue_instruction.destination);
                }
            }
        }
        // Immediate Instructions
        else if (Arrays.asList("ADDI", "ANDI", "ORI", "XORI", "SLL", "SRL", "SRA").contains(currentInstruction.Opcode)) {// RAW hazard detection
            if (Arrays.asList("ADD", "SUB", "MUL", "AND", "OR", "XOR", "NOR", "SLT").contains(preIssue_instruction.Opcode)) {
                if (!preIssue_instruction.issued) {
                    return (currentInstruction.source1 != preIssue_instruction.destination) // RAW, WAW, WAR
                            && (currentInstruction.destination != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.source1 && currentInstruction.destination != preIssue_instruction.source2);
                } else {  // previous instruction is issued, don't check for WAR
                    return (currentInstruction.source1 != preIssue_instruction.destination) // RAW, WAW
                            && (currentInstruction.destination != preIssue_instruction.destination);
                }
            } else if (Arrays.asList("ADDI", "ANDI", "ORI", "XORI", "SLL", "SRL", "SRA").contains(preIssue_instruction.Opcode)) {
                if (!preIssue_instruction.issued) {
                    return (currentInstruction.source1 != preIssue_instruction.destination) // RAW, WAW, WAR
                            && (currentInstruction.destination != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.source1);
                } else {  // previous instruction is issued, don't check for WAR
                    return (currentInstruction.source1 != preIssue_instruction.destination) // RAW, WAW
                            && (currentInstruction.destination != preIssue_instruction.destination);
                }
            }
        }
        // LW/SW Instructions
        else if (Arrays.asList("LW", "SW").contains(currentInstruction.Opcode)) {
            /* if previous instruction not issued */
            if (!preIssue_instruction.issued) {
                // LW must wait until all previous stores are issued
                if (Arrays.asList("ADD", "SUB", "MUL", "AND", "OR", "XOR", "NOR", "SLT").contains(preIssue_instruction.Opcode)) {
                    // check if destination of LW is != destination of previous instruction
                    // check if base of LW is != destination of previous instruction
                    // check if destination is != sources
                    return (currentInstruction.destination != preIssue_instruction.source1 && currentInstruction.destination != preIssue_instruction.source2)
                            && (currentInstruction.base != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.destination);
                } else if (Arrays.asList("ADDI", "ANDI", "ORI", "XORI", "SLL", "SRL", "SRA").contains(preIssue_instruction.Opcode)) {
                    return (currentInstruction.destination != preIssue_instruction.source1)
                            && (currentInstruction.base != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.destination);
                } else if (Objects.equals(preIssue_instruction.Opcode, "LW")) {
                    // check if previous lW is using current LW destination as its base
                    // check if current LW is writing to previous LW base
                    // check if destinations are equal
                    return (currentInstruction.destination != preIssue_instruction.base)
                            && (currentInstruction.base != preIssue_instruction.destination)
                            && (currentInstruction.destination != preIssue_instruction.destination);
                }
                if (Objects.equals(preIssue_instruction.Opcode, "SW"))
                    return false;

            }
            /* if previous instruction is issued */
            else {
                return (currentInstruction.destination != preIssue_instruction.destination)
                        && (currentInstruction.base != preIssue_instruction.destination);
            }
        }
        return false;
    }

    void executeFunctionalUnits(Map<Integer, MIPSsim> mappedInstructions, storageLocation storage, ArrayList<Integer> registers) {
        MIPSsim[] preIssueQueue_currentCycle = new MIPSsim[storage.PreIssue_Queue.size()];
        storage.PreIssue_Queue.toArray(preIssueQueue_currentCycle); // captures the PreIssue queue at the beginning of each cycle

        MIPSsim[] preALU2Queue_currentCycle = new MIPSsim[storage.PreALU2_Queue.size()];
        storage.PreALU2_Queue.toArray(preALU2Queue_currentCycle);   // captures the PreALU2 queue at the beginning of each cycle
        MIPSsim postALU2_currentCycle_I = null;
        int postALU2_currentCycle_result = 0;
        if (storage.PostALU2_Queue.getKey() != null) {
            postALU2_currentCycle_I = storage.PostALU2_Queue.getKey();
            postALU2_currentCycle_result = storage.PostALU2_Queue.getValue();
        }

        MIPSsim[] preALU1Queue_currentCycle = new MIPSsim[storage.PreALU1_Queue.size()];
        storage.PreALU1_Queue.toArray(preALU1Queue_currentCycle);   // captures the PreALU1 queue at the beginning of each cycle

        MIPSsim[] preMEMQueue_currentCycle = new MIPSsim[storage.PreMEM_Queue.size()];
        storage.PreMEM_Queue.toArray(preMEMQueue_currentCycle);
        MIPSsim postMEM_currentCycle_I = null;
        int postMEM_currentCycle_result = 0;
        if (storage.PostMEM_Queue.getKey() != null) {
            postMEM_currentCycle_result = storage.PostMEM_Queue.getValue();
            postMEM_currentCycle_I = storage.PostMEM_Queue.getKey();
        }

        IF(storage, mappedInstructions, registers);
        ISSUE(storage, preIssueQueue_currentCycle);
        ALU1(storage, preALU1Queue_currentCycle, registers);
        ALU2(storage, preALU2Queue_currentCycle, registers);
        MEM(storage, preMEMQueue_currentCycle, registers);
        WB(storage, postALU2_currentCycle_I, postALU2_currentCycle_result, postMEM_currentCycle_I, postMEM_currentCycle_result, registers);

    }

    void IF(storageLocation storage, Map<Integer, MIPSsim> mappedInstructions, ArrayList<Integer> registers) {
        MIPSsim obj = new MIPSsim();    // used for executing branches
        MIPSsim firstInstruction = mappedInstructions.get(storage.PC);
        MIPSsim secondInstruction;
        storage.IFexecuted = null; // clears IFexecuted from previous cycle

        if (Objects.equals(firstInstruction.Opcode, "BREAK")) {  // EXIT PROTOCOL!!
            storage.IFexecuted = firstInstruction;
            storage.PC += 4;
        } else {
            if (storage.IFwaiting != null) {    // Only fetch instructions if there is no branch waiting to be executed
                if (checkBranchRegisters(storage, storage.IFwaiting)) {
                    storage.PC = obj.executeInstruction(storage.IFwaiting, storage.PC, registers, storage);
                    storage.IFexecuted = storage.IFwaiting;
                    storage.IFwaiting = null; // remove branch from IFwaiting, move it to IFexecuted
                }
            } else {
                if (storage.PreIssue_Queue.size() < 4) {   // only fetch instructions when the pre-issue queue is not full
                    if (Arrays.asList("J", "JR", "BEQ", "BGTZ").contains(firstInstruction.Opcode)) {    // first instruction is a branch (don't fetch second instruction)
                        if (checkBranchRegisters(storage, firstInstruction)) {   // execute branch if registers are valid

                            storage.PC = obj.executeInstruction(firstInstruction, storage.PC, registers, storage);   // execute branch
                            storage.IFexecuted = firstInstruction;
                        } else {
                            storage.IFwaiting = firstInstruction;
                        }
                    } else {  // first instruction is not a branch
                        storage.PreIssue_Queue.add(firstInstruction);
                        storage.PC += 4;

                        if (storage.PreIssue_Queue.size() < 4) {    // fetch second instruction if there is room in pre-issue queue
                            secondInstruction = mappedInstructions.get(storage.PC); // fetch next instruction
                            if (Objects.equals(secondInstruction.Opcode, "BREAK")) {
                                storage.IFexecuted = secondInstruction;
                                storage.PC += 4;
                            }
                            if (Arrays.asList("J", "JR", "BEQ", "BGTZ").contains(secondInstruction.Opcode)) { // if second instruction is branched
                                if (checkBranchRegisters(storage, secondInstruction)) {   // execute branch if registers are valid
                                    storage.PC = obj.executeInstruction(secondInstruction, storage.PC, registers, storage);  // execute branch
                                    storage.IFexecuted = secondInstruction;
                                } else {
                                    storage.IFwaiting = secondInstruction;
                                }
                            } else {  // second instruction is not a branch, add to pre-issue queue
                                storage.PreIssue_Queue.add(secondInstruction);
                                storage.PC += 4;
                            }
                        }
                    }
                }
            }
        }
    }

    void ISSUE(storageLocation storage, MIPSsim[] currentQueue) {
        //TODO: Implement SW
        MIPSsim currentInstruction;
        boolean ALU1_issued = false;
        boolean ALU2_issued = false;    // only 1 instruction is issued to each ALU, these keep track of whether that's happened

        for (int i = 0; i < currentQueue.length; i++) {
            currentInstruction = currentQueue[i];
            if (i == 0) {
                if (checkInstructionRegisters(storage, currentInstruction)) {  // registers are valid
                    if (!Arrays.asList("SW", "LW").contains(currentInstruction.Opcode) && storage.PreALU2_Queue.size() != 2) {  // ALU2 instructions
                        storage.PreALU2_Queue.add(currentInstruction);
                        ALU2_issued = true;
                        currentInstruction.issued = true;
                        storage.PreIssue_Queue.remove(currentInstruction);  // remove from preIssue queue
                        //storage.PreIssue_Queue.toArray(currentQueue);  // update array, updating reduces size of array, causing error in for loop addressing
                        storage.validRegisters.set(currentInstruction.destination, 0);  // set destination register as invalid
                    } else if (Arrays.asList("SW", "LW").contains(currentInstruction.Opcode) && storage.PreALU1_Queue.size() != 2) {
                        storage.PreALU1_Queue.add(currentInstruction);
                        ALU1_issued = true;
                        currentInstruction.issued = true;
                        storage.PreIssue_Queue.remove(currentInstruction);

                        if (Objects.equals(currentInstruction.Opcode, "LW"))
                            storage.validRegisters.set(currentInstruction.destination, 0);
                    }
                }
                // first instruction...
                // check if its sources are valid, if so push to appropriate ALU
                // if it is pushed, mark its write register as invalid
            } else {
                boolean instruction_ready = true;
                for (int j = i - 1; j >= 0; j--) { // checks for WAW and RAW for issued + non-issued instructions
                    instruction_ready = checkPreIssueQueueRegisters(currentInstruction, currentQueue[j]);

                    // starts as true, loops through each of the instructions and checks each appropriate register
                    // if by the end of this loop the boolean is still true, the instruction can be issued and dealt with appropriately.
                    // also need to keep track that only one ALU1 and one ALU2 are being issued per cycle.
                }

                if (instruction_ready) {
                    if (checkInstructionRegisters(storage, currentInstruction)) {  // registers are valid
                        if (!Arrays.asList("SW", "LW").contains(currentInstruction.Opcode) && storage.PreALU2_Queue.size() != 2 && !ALU2_issued) {  // ALU2 instructions
                            storage.PreALU2_Queue.add(currentInstruction);
                            ALU2_issued = true;
                            currentInstruction.issued = true;
                            storage.PreIssue_Queue.remove(currentInstruction);  // remove from preIssue queue
                            storage.PreIssue_Queue.toArray(currentQueue);  // update array

                            storage.validRegisters.set(currentInstruction.destination, 0);  // set destination register as invalid
                        } else if (Objects.equals(currentInstruction.Opcode, "LW") && storage.PreALU1_Queue.size() != 2 && !ALU1_issued) {
                            storage.PreALU1_Queue.add(currentInstruction);
                            ALU1_issued = true;
                            currentInstruction.issued = true;
                            storage.PreIssue_Queue.remove(currentInstruction);
                            storage.validRegisters.set(currentInstruction.destination, 0);

                        }
                    }
                }
            }
        }


    }

    void ALU1(storageLocation storage, MIPSsim[] currentQueue, ArrayList<Integer> registers) {
        MIPSsim instruction;
        if (currentQueue.length > 0) {
            instruction = currentQueue[0];
            instruction.LW_SW_offsetBase = registers.get(instruction.base) + instruction.offset;    // store address calculation in base, ignore offset after this
            if (storage.PreMEM_Queue.size() > 0) { // if there's an instruction, remove it because MEM will handle it during this cycle
                storage.PreMEM_Queue.poll();
            }
            storage.PreMEM_Queue.add(instruction);  // store instruction in PreMem queue
            storage.PreALU1_Queue.remove(instruction);

        }
    }

    void ALU2(storageLocation storage, MIPSsim[] currentQueue, ArrayList<Integer> registers) {
        MIPSsim instruction;
        if (currentQueue.length > 0) {
            instruction = currentQueue[0];
            storage.PostALU2_Queue = new Pair(instruction, instruction.ALU2(instruction, registers));
            storage.PreALU2_Queue.remove(instruction);
        } else
            storage.PostALU2_Queue.instruction = null;   // if there are no instructions coming in, the last instruction in the postALU2 needs to be cleared
    }

    void MEM(storageLocation storage, MIPSsim[] currentQueue, ArrayList<Integer> registers) {
        MIPSsim instruction;
        if (currentQueue.length > 0) {
            instruction = currentQueue[0];

            if (Objects.equals(instruction.Opcode, "LW")) {
                storage.PostMEM_Queue = new Pair(instruction, instruction.MEM(instruction, registers, storage));
                storage.PreMEM_Queue.remove(instruction);
            }
            // SW
            else {
                instruction.MEM(instruction, registers, storage);
                storage.PreMEM_Queue.remove(instruction);
            }
        } else {
            storage.PostMEM_Queue.instruction = null;
        }
    }

    void WB(storageLocation storage, MIPSsim ALU2_Instruction, int ALU2result, MIPSsim MEM_Instruction, int MEMresult, ArrayList<Integer> registers) {
        //updates valid registers
        if (ALU2_Instruction != null) {
            registers.set(ALU2_Instruction.destination, ALU2result);
            storage.validRegisters.set(ALU2_Instruction.destination, 1);
        }
        if (MEM_Instruction != null) {
            registers.set(MEM_Instruction.destination, MEMresult);
            storage.validRegisters.set(MEM_Instruction.destination, 1);
        }
    }
}

class MIPSsim {
    // representations
    String binary;
    String parsed = "";

    // ===========MIPSsim components=============== //
    int categoryIdentifier = -1;
    int PC = 0;
    String Opcode;
    int destination = -1;
    int source1 = -1;
    int source2 = -1;    // only when MIPSsim is category 1
    int immediateValue = -1;    // only when MIPSsim is category 2
    int offset = -1;
    int base = -1;  // Load/Store offset base
    int LW_SW_offsetBase = -1;
    boolean issued = false; // used for WAR hazard detection. Identifies whether instruction has already been issued

    //================== FILE I\O METHODS ===============================//

    ArrayList<String> memoryAddresses_binary = new ArrayList<>();   //stores binary value of address values to be printed in disassembly

    String twosComplement(String binary) {
        StringBuilder compliment = new StringBuilder(binary);
        int index = 0;
        for (int i = binary.length() - 1; i >= 0; i--) {
            if (binary.charAt(i) == '1') {
                index = i;
                break;
            }
        }
        for (int i = 0; i < binary.length(); i++) {
            if (i < index) {
                if (binary.charAt(i) == '0')
                    compliment.setCharAt(i, '1');
                else if (binary.charAt(i) == '1')
                    compliment.setCharAt(i, '0');
            }
        }
        binary = compliment.toString();
        return binary;
    }

    ArrayList<MIPSsim> readFile(String fileName, storageLocation storage) {
        ArrayList<MIPSsim> MIPSsims = new ArrayList<>();
        int addressValue;

        int PC = 256;
        File inputFile = new File(fileName);
        try {
            Scanner scanner = new Scanner(inputFile);
            while (scanner.hasNextLine()) {
                MIPSsim instr = new MIPSsim();
                instr.binary = scanner.nextLine();
                instr.PC = PC;
                MIPSsims.add(instr);

                if (instr.binary.startsWith("010101")) {
                    String temp;
                    while (scanner.hasNextLine()) {
                        PC += 4;
                        temp = scanner.nextLine();  //temporarily holds the value of the string from binary file
                        if (temp.startsWith("1"))   // Initial bit 1 signifies negative number and thus two's compliment computation
                            addressValue = -1 * Integer.parseInt(twosComplement(temp), 2);   //twos complement of value to be stored at the address
                        else
                            addressValue = Integer.parseInt(temp, 2);
                        storage.memoryAddresses.put(PC, addressValue);  //store in hashmap as a <PC, Value> pair
                        memoryAddresses_binary.add(temp);

                    }
                } else {
                    PC += 4;
                }

            }
            scanner.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return MIPSsims;
    }

    void writeDisassembly(ArrayList<MIPSsim> MIPSsims, storageLocation storage) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("disassembly.txt"))) {

            for (MIPSsim i : MIPSsims) {
                if (i.Opcode.equals("BREAK")) {
                    writer.write(i.binary + "\t" + i.PC + "\t" + i.Opcode);
                    writer.newLine();
                    int addressPC = i.PC + 4;   // PC values for memory values
                    int j = 0;
                    while (storage.memoryAddresses.containsKey(addressPC)) { // Loop over memory addresses to print to disassembly file
                        writer.write(memoryAddresses_binary.get(j) + "\t" + addressPC + "\t" + storage.memoryAddresses.get(addressPC));
                        writer.newLine();
                        addressPC += 4;
                        j++;
                    }
                } else {
                    writer.write(i.binary + "\t" + i.PC + "\t" + i.parsed);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    void writeSimulation(ArrayList<Integer> registers, ArrayList<MIPSsim> MIPSsims,
                         storageLocation storage, functionalUnit FU) {
        int cycle = 1;
        Map<Integer, MIPSsim> mappedInstructions = new HashMap<>();

        for (MIPSsim MIPSsim : MIPSsims) {
            mappedInstructions.put(MIPSsim.PC, MIPSsim);   //Maps PC to its MIPSsim
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("simulation.txt"))) {
            while (mappedInstructions.containsKey(storage.PC)) {
                FU.executeFunctionalUnits(mappedInstructions, storage, registers);

                writer.write("--------------------\n");
                writer.write("Cycle " + cycle + ":\n");
                writer.newLine();

                writer.write("IF Unit:\n");
                writer.write("\tWaiting:");
                if (storage.IFwaiting != null) {
                    writer.write(" [" + storage.IFwaiting.parsed);
                    writer.write("]");
                }
                writer.newLine();
                writer.write("\tExecuted:");
                if (storage.IFexecuted != null) {
                    writer.write(" [" + storage.IFexecuted.parsed);
                    writer.write("]");
                }
                writer.newLine();

                writer.write("Pre-Issue Queue:\n");
                int j = 0;
                for (MIPSsim s : storage.PreIssue_Queue) {
                    writer.write("\tEntry " + j + ": " + "[" + s.parsed + "]");
                    writer.newLine();
                    j++;
                }
                for (int k = j; k < 4; k++) {
                    writer.write("\tEntry " + k + ":");
                    writer.newLine();
                }

                writer.write("Pre-ALU1 Queue:\n");
                j = 0;
                for (MIPSsim s : storage.PreALU1_Queue) {
                    writer.write("\tEntry " + j + ": " + "[" + s.parsed + "]");
                    writer.newLine();
                    j++;
                }
                for (int k = j; k < 2; k++) {
                    writer.write("\tEntry " + k + ":");
                    writer.newLine();
                }

                writer.write("Pre-MEM Queue:");
                if (storage.PreMEM_Queue.size() == 1)
                    writer.write(" [" + storage.PreMEM_Queue.peek().parsed + "]");
                writer.newLine();

                writer.write("Post-MEM Queue:");
                if (storage.PostMEM_Queue.instruction != null)
                    writer.write(" [" + storage.PostMEM_Queue.getKey().parsed + "]");
                writer.newLine();

                writer.write("Pre-ALU2 Queue:\n");
                j = 0;
                for (MIPSsim s : storage.PreALU2_Queue) {
                    writer.write("\tEntry " + j + ": " + "[" + s.parsed + "]");
                    writer.newLine();
                    j++;
                }
                for (int k = j; k < 2; k++) {
                    writer.write("\tEntry " + k + ":");
                    writer.newLine();
                }
                writer.write("Post-ALU2 Queue:");
                if (storage.PostALU2_Queue.instruction != null)
                    writer.write(" [" + storage.PostALU2_Queue.getKey().parsed + "]");
                writer.newLine();
                writer.newLine();

                //storage.PC = executeInstruction(mappedInstructions.get(storage.PC), storage.PC, registers);

                writer.write("Registers\n");
                writer.write("R00:\t" + registers.get(0) + "\t" + registers.get(1) + "\t" + registers.get(2) + "\t" + registers.get(3) + "\t" + registers.get(4) + "\t" + registers.get(5) + "\t" + registers.get(6) + "\t" + registers.get(7) + "\n");
                writer.write("R08:\t" + registers.get(8) + "\t" + registers.get(9) + "\t" + registers.get(10) + "\t" + registers.get(11) + "\t" + registers.get(12) + "\t" + registers.get(13) + "\t" + registers.get(14) + "\t" + registers.get(15) + "\n");
                writer.write("R16:\t" + registers.get(16) + "\t" + registers.get(17) + "\t" + registers.get(18) + "\t" + registers.get(19) + "\t" + registers.get(20) + "\t" + registers.get(21) + "\t" + registers.get(22) + "\t" + registers.get(23) + "\n");
                writer.write("R24:\t" + registers.get(24) + "\t" + registers.get(25) + "\t" + registers.get(26) + "\t" + registers.get(27) + "\t" + registers.get(28) + "\t" + registers.get(29) + "\t" + registers.get(30) + "\t" + registers.get(31));
                writer.newLine();
                writer.newLine();
                writer.write("Data\n" + (MIPSsims.get(MIPSsims.size() - 1).PC + 4) + ":\t");

                int index = MIPSsims.get(MIPSsims.size() - 1).PC + 4;
                int step = 1;

                while (storage.memoryAddresses.containsKey(index)) {
                    if (step % 8 == 0 && storage.memoryAddresses.containsKey(index + 4)) {
                        writer.write(storage.memoryAddresses.get(index) + "\n");
                        writer.write((index + 4) + ":\t");
                    } else {
                        if (!storage.memoryAddresses.containsKey(index + 4))
                            writer.write(storage.memoryAddresses.get(index) + "\n");
                        else
                            writer.write(storage.memoryAddresses.get(index) + "\t");

                    }

                    step++;
                    index += 4;
                }
                cycle++;
            }

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }   // WIP

    int ALU2(MIPSsim instruction, ArrayList<Integer> registers) {
        String op = instruction.Opcode;
        int source1 = 0;
        int source2 = 0;

        switch (op) {
            case "ADD":
            case "SUB":
            case "MUL":
            case "AND":
            case "OR":
            case "XOR":
            case "SLT":
            case "NOR":
                source1 = registers.get(instruction.source1);
                source2 = registers.get(instruction.source2);
                break;
            case "ADDI":
            case "ANDI":
            case "XORI":
            case "ORI":
            case "SLL":
            case "SRL":
            case "SRA":
                source1 = registers.get(instruction.source1);
                break;

        }
        switch (instruction.Opcode) {
            case ("ADD"):
                return source1 + source2;
            case ("SUB"):
                return source1 - source2;
            case ("MUL"):
                return source1 * source2;
            case ("AND"):
                return source1 & source2;
            case ("XOR"):
                return source1 ^ source2;
            case ("NOR"):   //TODO DOUBLE CHECK THIS OPERATION
                return ~(source1 | source2);
            case ("SLT"):
                if (source1 < source2)
                    return 1;
                else return 0;
            case ("ADDI"):
                return source1 + immediateValue;
            case ("ANDI"):
                return source1 & immediateValue;
            case ("ORI"):
                return source1 | immediateValue;
            case ("XORI"):
                return source1 ^ immediateValue;
            case ("SLL"):
                return source1 << immediateValue;
            case ("SRL"):
                return source1 >>> immediateValue;
            case ("SRA"):
                return source1 >> immediateValue;
        }
        return 999;
    }

    int MEM(MIPSsim instruction, ArrayList<Integer> registers, storageLocation storage) {
        String op = instruction.Opcode;
        int destination = 0;
        int base = 0;

        switch (op) {
            case "SW":
            case "LW":
                destination = instruction.destination;
                base = instruction.LW_SW_offsetBase;
                break;
        }
        switch (instruction.Opcode) {
            case ("SW"):
                storage.memoryAddresses.put(base, registers.get(destination));
                return 0;   // return is arbitrary b/c SW is just writing to memory address
            case ("LW"):
                return storage.memoryAddresses.get(base);   // this value will be returned and stored in LW destination
        }
        return 999;
    }

    int executeInstruction(MIPSsim MIPSsim, int PC, ArrayList<Integer> registers, storageLocation storage) {
        String op = MIPSsim.Opcode;
        int source1 = 0;
        int source2 = 0;
        int destination = 0;
        int offset = 0;
        int base = 0;
        int immediateValue = 0;

        switch (op) {
            case "ADD":
            case "SUB":
            case "MUL":
            case "AND":
            case "OR":
            case "XOR":
            case "SLT":
            case "NOR":
                source1 = registers.get(MIPSsim.source1);
                source2 = registers.get(MIPSsim.source2);
                destination = MIPSsim.destination;
                break;
            case "ADDI":
            case "ANDI":
            case "XORI":
            case "ORI":
            case "SLL":
            case "SRL":
            case "SRA":
                destination = MIPSsim.destination;
                source1 = registers.get(MIPSsim.source1);
                immediateValue = MIPSsim.immediateValue;
                break;
            case "BEQ":
                source1 = registers.get(MIPSsim.source1);
                source2 = registers.get(MIPSsim.source2);
                offset = MIPSsim.offset;
                break;
            case "BLTZ":
            case "BGTZ":
                source1 = registers.get(MIPSsim.source1);
                offset = MIPSsim.offset;
                break;
            case "SW":
            case "LW":
                destination = MIPSsim.destination;
                base = registers.get(MIPSsim.base);
                offset = MIPSsim.offset;
                break;
        }

        switch (MIPSsim.Opcode) {
            case ("ADD"):
                registers.set(destination, source1 + source2);
                PC += 4;
                break;
            case ("SUB"):
                registers.set(destination, source1 - source2);
                PC += 4;
                break;
            case ("MUL"):
                registers.set(destination, source1 * source2);
                PC += 4;
                break;
            case ("AND"):
                registers.set(destination, source1 & source2);
                PC += 4;
                break;
            case ("XOR"):
                registers.set(destination, source1 ^ source2);
                PC += 4;
                break;
            case ("NOR"):   //TODO DOUBLE CHECK THIS OPERATION
                registers.set(destination, ~(source1 | source2));
                PC += 4;
                break;
            case ("SLT"):
                if (source1 < source2)
                    registers.set(destination, 1);
                else registers.set(destination, 0);
                PC += 4;
                break;
            case ("ADDI"):
                registers.set(destination, source1 + immediateValue);
                PC += 4;
                break;
            case ("ANDI"):
                registers.set(destination, source1 & immediateValue);
                PC += 4;
                break;
            case ("ORI"):
                registers.set(destination, source1 | immediateValue);
                PC += 4;
                break;
            case ("XORI"):
                registers.set(destination, source1 ^ immediateValue);
                PC += 4;
                break;
            case ("BEQ"):
                if (source1 == source2)
                    PC = PC + 4 + offset;
                else
                    PC += 4;
                break;
            case ("BLTZ"):
                if (source1 < 0)
                    PC = PC + 4 + offset;
                else
                    PC += 4;
                break;
            case ("BGTZ"):
                if (source1 > 0)
                    PC = PC + 4 + offset;
                else
                    PC += 4;
                break;
            case ("SW"):
                storage.memoryAddresses.put(base + offset, registers.get(destination));
                PC += 4;
                break;
            case ("LW"):
                registers.set(destination, storage.memoryAddresses.get(base + offset));
                PC += 4;
                break;
            case ("SLL"):
                registers.set(destination, source1 << immediateValue);
                PC += 4;
                break;
            case ("SRL"):
                registers.set(destination, source1 >>> immediateValue);
                PC += 4;
                break;
            case ("SRA"):
                registers.set(destination, source1 >> immediateValue);
                PC += 4;
                break;
            case ("J"):
                PC = MIPSsim.immediateValue;
                break;
            case ("JR"):
                PC = registers.get(MIPSsim.source1);
                break;
            case ("OR"):
                registers.set(destination, source1 | source2);
                PC += 4;
                break;
            case ("BREAK"):
                PC += 4;
                break;
            default:
                PC += 4;
                break;
        }
        return PC;
    }

    //============== PARSING METHODS ===============//
    void parseCategory() {
        String identifier = binary.substring(0, 2);

        if (identifier.equals("01"))
            categoryIdentifier = 1;
        else if (identifier.equals("11"))
            categoryIdentifier = 2;
        else
            throw new RuntimeException("ERROR: INVALID CATEGORY IDENTIFIER");
    }

    void parseOpcode() {
        Opcode = binary.substring(2, 6);

        switch (Opcode) {
            case "0000":
                Opcode = (categoryIdentifier == 1) ? "J" : "ADD";
                break;
            case "0001":
                Opcode = (categoryIdentifier == 1) ? "JR" : "SUB";
                break;
            case "0010":
                Opcode = (categoryIdentifier == 1) ? "BEQ" : "MUL";
                break;
            case "0011":
                Opcode = (categoryIdentifier == 1) ? "BLTZ" : "AND";
                break;
            case "0100":
                Opcode = (categoryIdentifier == 1) ? "BGTZ" : "OR";
                break;
            case "0101":
                Opcode = (categoryIdentifier == 1) ? "BREAK" : "XOR";
                break;
            case "0110":
                Opcode = (categoryIdentifier == 1) ? "SW" : "NOR";
                break;
            case "0111":
                Opcode = (categoryIdentifier == 1) ? "LW" : "SLT";
                break;
            case "1000":
                Opcode = (categoryIdentifier == 1) ? "SLL" : "ADDI";
                break;
            case "1001":
                Opcode = (categoryIdentifier == 1) ? "SRL" : "ANDI";
                break;
            case "1010":
                Opcode = (categoryIdentifier == 1) ? "SRA" : "ORI";
                break;
            case "1011":
                Opcode = (categoryIdentifier == 1) ? "NOP" : "XORI";
                break;
        }
    }

    void parseSources() {
        if (categoryIdentifier == 1) {
            switch (Opcode) {
                case "J":
                    immediateValue = Integer.parseInt(binary.substring(6, 32), 2) << 2;
                    break;
                case "JR":
                    source1 = Integer.parseInt(binary.substring(6, 11), 2);
                    break;
                case "BEQ":
                    source1 = Integer.parseInt(binary.substring(6, 11), 2);
                    source2 = Integer.parseInt(binary.substring(11, 16), 2);
                    offset = Integer.parseInt(binary.substring(16, 32), 2) << 2;   // JUMP INSTRUCTIONS ARE LEFT SHIFTED TWICE
                    break;
                case "BGTZ":
                case "BLTZ":
                    source1 = Integer.parseInt(binary.substring(6, 11), 2);
                    offset = Integer.parseInt(binary.substring(16, 32), 2) << 2;
                    break;
                case "LW":
                case "SW":
                    base = Integer.parseInt(binary.substring(6, 11), 2);
                    destination = Integer.parseInt(binary.substring(11, 16), 2);
                    offset = Integer.parseInt(binary.substring(16, 32), 2);
                    break;
                case "SLL":
                case "SRL":
                case "SRA":
                    //case "NOP": evaluated as SLL R0, R0, #0
                    source1 = Integer.parseInt(binary.substring(11, 16), 2);
                    destination = Integer.parseInt(binary.substring(16, 21), 2);
                    immediateValue = Integer.parseInt(binary.substring(21, 26), 2);    //Number of bits to shift

            }

        } else {  // CATEGORY 2 OPERATIONS
            if (Objects.equals(Opcode, "ADDI") || Objects.equals(Opcode, "ANDI") || Objects.equals(Opcode, "ORI") || Objects.equals(Opcode, "XORI")) {

                source1 = Integer.parseInt(binary.substring(6, 11), 2);
                destination = Integer.parseInt(binary.substring(11, 16), 2);
                immediateValue = Integer.parseInt(binary.substring(16, 32), 2);
            } else {    // ADD, SUB, MUL, AND, OR, XOR, NOR, SLT
                source1 = Integer.parseInt(binary.substring(6, 11), 2);
                source2 = Integer.parseInt(binary.substring(11, 16), 2);
                destination = Integer.parseInt(binary.substring(16, 21), 2);
            }

        }
    }

    void parseBinary() {
        parseCategory();
        parseOpcode();
        parseSources();

        if (Opcode.equals("J")) {
            parsed = (Opcode + " " + "#" + immediateValue);
        } else if (Opcode.equals("BREAK")) {
            parsed = Opcode;
        } else if (Opcode.equals("JR")) {
            parsed = (Opcode + " " + "R" + source1);
        } else if (Opcode.equals("BEQ")) {
            parsed = (Opcode + " R" + source1 + ", R" + source2 + ", #" + offset);
        } else if (Opcode.equals("BGTZ") || Opcode.equals("BLTZ")) {
            parsed = (Opcode + " R" + source1 + ", #" + offset);
        } else if (Opcode.equals("LW") || Opcode.equals("SW")) {
            parsed = (Opcode + " R" + destination + ", " + offset + "(R" + base + ")");
        } else if (Opcode.equals("SLL") || Opcode.equals("SRL") || Opcode.equals("SRA")) {
            parsed = (Opcode + " R" + destination + ", R" + source1 + ", #" + immediateValue);
        } else if (categoryIdentifier == 2) {
            if (Opcode.equals("ADDI") || Opcode.equals("ANDI") || Opcode.equals("ORI") || Opcode.equals("XORI")) {
                parsed = (Opcode + " R" + destination + ", R" + source1 + ", #" + immediateValue);
            } else {
                parsed = (Opcode + " R" + destination + ", R" + source1 + ", R" + source2);
            }
        }
    }

    public static void main(String[] args) {
        String fileName = args[0];

        storageLocation storage = new storageLocation();
        functionalUnit FU = new functionalUnit();

        ArrayList<MIPSsim> MIPSsims;
        for (int i = 0; i < 32; i++) {                        // INITIALIZING REGISTERS
            storage.registers.add(0);
            storage.validRegisters.add(1);
        }
        MIPSsim obj = new MIPSsim();
        MIPSsims = obj.readFile(fileName, storage);

        for (MIPSsim MIPSsim : MIPSsims)
            MIPSsim.parseBinary();

        obj.writeDisassembly(MIPSsims, storage);
        obj.writeSimulation(storage.registers, MIPSsims, storage, FU);
    }

}
