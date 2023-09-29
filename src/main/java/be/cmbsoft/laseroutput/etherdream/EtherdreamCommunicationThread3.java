package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import be.cmbsoft.ilda.IldaPoint;

import static be.cmbsoft.laseroutput.etherdream.Etherdream.log;

public class EtherdreamCommunicationThread3 extends Thread
{
    private final InetAddress     address;
    private final Etherdream      etherdream;
    private       boolean         halted;
    private       Socket          socket;
    private       OutputStream    output;
    private       InputStream     input;
    private       int             targetPps;
    private       List<IldaPoint> nextFrame;
    private       List<IldaPoint> currentFrame;
    private       State           state = State.INIT;

    EtherdreamCommunicationThread3(InetAddress address, Etherdream etherdream)
    {
        setName("EtherdreamCommunicationThread");
        this.address = address;
        this.etherdream = etherdream;
    }

    public boolean isHalted()
    {
        return halted;
    }

    @Override
    public void run()
    {
        boolean expectStatus = true;
        while (!halted)
        {
            try
            {
                if (socket == null || socket.isClosed())
                {
                    connect();
                }
                boolean    endOfStream = false;
                ByteBuffer buffer      = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                int receivedChars = 0;
                while (!endOfStream)
                {
                    // Blocks until we received an input from the Etherdream
                    int b = input.read();
                    if (b < 0 || ++receivedChars >= buffer.capacity())
                    {
                        endOfStream = true;
                    }
                    else
                    {
                        buffer.put((byte) (b & 0xff));
//                        System.out.println((byte) (b & 0xff) + " | " + (char) b);
                    }
                }
                // When we didn't send a message, we expect periodic status updates (one every second)
                if (expectStatus)
                {
                    EtherdreamStatus etherdreamStatus = new EtherdreamStatus(buffer);
                }
                else
                {

                    EtherdreamResponse       response = processResponse(buffer.array());
                    EtherdreamResponseStatus status   = EtherdreamResponseStatus.get(response.getResponse().state);
                    if (status == EtherdreamResponseStatus.ACK)
                    {
                        state = state.stateWhenAck(this);
                    }
                    else
                    {
                        state = state.stateWhenNak(this);
                    }
                }

                EtherdreamCommand messageToSend = state.generateMessage();
                if (messageToSend != null)
                {
                    log("Sending command " + messageToSend.getCommandChar());
                    output.write(messageToSend.getBytes());
                    output.flush();
                    expectStatus = false;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
                halted = true;
                Thread.currentThread().interrupt();
            }
        }
        if (socket != null)

        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        etherdream.setConnectionFailed();

    }

    private boolean hasFrame()
    {
        return nextFrame != null && !nextFrame.isEmpty();
    }

    private EtherdreamResponse processResponse(byte[] array)
    {
        return new EtherdreamResponse(array);
    }

    public void project(List<IldaPoint> points, int pps)
    {
        this.targetPps = pps;
        this.nextFrame = points;
    }

    public void halt()
    {
        halted = true;
    }

    private void connect() throws IOException
    {
        socket = new Socket(address, 7765);
        socket.setSoTimeout(5000);
        output = socket.getOutputStream();
        input = socket.getInputStream();
    }

    enum State
    {
        INIT
            {
                @Override
                State stateWhenAck(EtherdreamCommunicationThread3 thread)
                {
                    return thread.hasFrame() ? CHECK_STATUS : INIT;
                }

                @Override
                State stateWhenNak(EtherdreamCommunicationThread3 thread)
                {
                    return INIT;
                }

                @Override
                EtherdreamCommand generateMessage()
                {
                    return null;
                }
            },
        CHECK_STATUS
            {
                @Override
                State stateWhenAck(EtherdreamCommunicationThread3 thread)
                {
                    return PLAYBACK;
                }

                @Override
                State stateWhenNak(EtherdreamCommunicationThread3 thread)
                {
                    return STOP;
                }

                @Override
                EtherdreamCommand generateMessage()
                {
                    return null;
                }
            }, PLAYBACK
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread3 thread)
            {
                return SET_POINT_RATE;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread3 thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage()
            {
                return null;
            }
        }, STOP
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread3 thread)
            {
                return INIT;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread3 thread)
            {
                return INIT;
            }

            @Override
            EtherdreamCommand generateMessage()
            {
                return new EtherdreamStopCommand();
            }
        };

        abstract State stateWhenAck(EtherdreamCommunicationThread3 thread);

        abstract State stateWhenNak(EtherdreamCommunicationThread3 thread);

        abstract EtherdreamCommand generateMessage();
    }

}
