package be.cmbsoft.laseroutput.etherdream;

public class EtherdreamPingCommand implements EtherdreamCommand
{

    @Override
    public byte[] getBytes()
    {
        return new byte[]{(byte) '?'};
    }

    @Override
    public char getCommandChar()
    {
        return '?';
    }

}
