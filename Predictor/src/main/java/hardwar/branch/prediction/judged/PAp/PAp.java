package hardwar.branch.prediction.judged.PAp;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

import static java.lang.Math.pow;

public class PAp implements BranchPredictor {

    private final int branchInstructionSize;

    private final ShiftRegister SC; // saturating counter register

    private final RegisterBank PABHR; // per address branch history register

    private final Cache<Bit[], Bit[]> PAPHT; // Per Address Predication History Table

    public PAp() {
        this(4, 2, 8);
    }

    public PAp(int BHRSize, int SCSize, int branchInstructionSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, (int) pow(2, BHRSize), SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        Bit[] history = getCacheEntry(branchInstruction.getInstructionAddress(), PABHR.read(branchInstruction.getInstructionAddress()).read());
        PAPHT.putIfAbsent(history, getDefaultBlock());
        SC.load(PAPHT.get(history));
        return (SC.read()[0] == Bit.ZERO) ? BranchResult.NOT_TAKEN : BranchResult.TAKEN;
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        Bit[] counter = CombinationalLogic.count(SC.read(), actual == BranchResult.TAKEN, CountMode.SATURATING);
        PAPHT.put(getCacheEntry(instruction.getInstructionAddress(), PABHR.read(instruction.getInstructionAddress()).read()), counter);
        ShiftRegister temp = PABHR.read(instruction.getInstructionAddress());
        temp.insert(Bit.of(actual == BranchResult.TAKEN));
        PABHR.write(instruction.getInstructionAddress(), temp.read());
    }


    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return "PAp predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PAPHT.monitor();
    }
}
