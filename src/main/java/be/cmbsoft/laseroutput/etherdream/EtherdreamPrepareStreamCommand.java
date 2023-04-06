package be.cmbsoft.laseroutput.etherdream;

public class EtherdreamPrepareStreamCommand implements EtherdreamCommand
{
    @Override
    public byte[] getBytes()
    {
        return new byte[]{(byte) 'p'};
    }

    @Override
    public char getCommandChar()
    {
        return 'p';
    }
}
