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
                    return thread.hasFrame() ? CHECK_STATUS : INIT;
                }

                @Override
                EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
                {
                    return null;
                }
            },
        CHECK_STATUS
            {
                @Override
                State stateWhenAck(EtherdreamCommunicationThread3 thread)
                {
                    return PREPARE_STREAM;
                }

                @Override
                State stateWhenNak(EtherdreamCommunicationThread3 thread)
                {
                    return STOP;
                }

                @Override
                EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
                {
                    return new EtherdreamPingCommand();
                }
            }, PREPARE_STREAM
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
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
            {
                return new EtherdreamPrepareStreamCommand();
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
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
            {
                return new EtherdreamStopCommand();
            }
        }, SET_POINT_RATE
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread3 thread)
            {
                return SEND_DATA;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread3 thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
            {
                return new EtherdreamPointRateCommand(thread.targetPps);
            }
        }, SEND_DATA
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread3 thread)
            {
                return PROJECT;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread3 thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
            {
                return new EtherdreamWriteDataCommand(thread.getCurrentFrameAndClear());
            }
        }, PROJECT
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread3 thread)
            {
                return thread.hasFrame() ? SEND_DATA : STOP;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread3 thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
            {
                return new EtherdreamBeginPlaybackCommand(30000);
            }
        };

        abstract State stateWhenAck(EtherdreamCommunicationThread3 thread);

        abstract State stateWhenNak(EtherdreamCommunicationThread3 thread);

        State stateWhenTimeout(EtherdreamCommunicationThread3 thread)
        {
            return this;
        }

        abstract EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread);
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

    private List<IldaPoint> getCurrentFrameAndClear()
    {
        currentFrame = nextFrame;
        return currentFrame;
    }

    @Override
    public void run()
    {
        System.out.println("starting etherdream communicaiton thread");
        while (!halted)
        {
            try
            {
                if (socket == null || socket.isClosed())
                {
                    connect();
                }
                socket.setSoTimeout(100);
                boolean    endOfStream = false;
                ByteBuffer buffer      = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                int     receivedChars = 0;
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
                    }
                }
                System.out.println("message received from etherdream");

                EtherdreamResponse       response = processResponse(buffer.array());
                EtherdreamResponseStatus status   = EtherdreamResponseStatus.get(response.getResponse().state);
                State oldState = state;
                if (status == EtherdreamResponseStatus.ACK)
                {
                    state = state.stateWhenAck(this);
                }
                else
                {
                    state = state.stateWhenNak(this);
                }
                if (oldState != state) {
                    log("State updated from " + oldState + " to " + state);
                }
                System.out.println(response);


                EtherdreamCommand messageToSend = state.generateMessage(this);
                if (messageToSend != null)
                {
                    log("Sending command " + messageToSend.getCommandChar());
                    output.write(messageToSend.getBytes());
                    output.flush();
                }
            }
            catch (IOException e)
            {
                State oldState = state;
                state = state.stateWhenTimeout(this);
                log("Etherdream wasn't fast enough, we're now in state " + state);
                if (oldState != state) {
                    log("State updated from " + oldState + " to " + state);
                }
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

}
