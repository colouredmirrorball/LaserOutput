package be.cmbsoft.laseroutput.etherdream;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class EtherdreamPointRateCommand implements EtherdreamCommand {

    private final byte[] bytes;

    public EtherdreamPointRateCommand(int pointRate) {

        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.order(ByteOrder.LITTLE_ENDIAN);


        buffer.put((byte) 'q');
        buffer.putInt(pointRate);


        bytes = buffer.array();
    }


    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public char getCommandChar() {
        return 'q';
    }
}
