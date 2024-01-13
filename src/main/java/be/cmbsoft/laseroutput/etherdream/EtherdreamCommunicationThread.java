package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import be.cmbsoft.ilda.IldaPoint;

import static be.cmbsoft.laseroutput.etherdream.Etherdream.log;
import static be.cmbsoft.laseroutput.etherdream.Etherdream.logException;
import static be.cmbsoft.laseroutput.etherdream.EtherdreamPlaybackState.PLAYING;
import static be.cmbsoft.laseroutput.etherdream.EtherdreamPlaybackState.PREPARED;

public class EtherdreamCommunicationThread extends Thread
{
    private final InetAddress        address;
    private final Etherdream         etherdream;
    private final int                maxBufferSize;
    private       boolean            halted;
    private       Socket             socket;
    private       OutputStream       output;
    private       InputStream        input;
    private       int                targetPps;
    private       List<IldaPoint>    nextFrame;
    private       List<IldaPoint>    currentFrame;
    private       State              state = State.INIT;
    private       EtherdreamResponse lastResponse;

    EtherdreamCommunicationThread(InetAddress address, Etherdream etherdream)
    {
        setName("EtherdreamCommunicationThread");
        this.address = address;
        this.etherdream = etherdream;
        this.maxBufferSize = etherdream.getBroadcast().getBufferCapacity();
    }

    @Override
    public void run()
    {
        // DO NOT FIX THE TYPO BELOW!!! Everything breaks when you do.
        log("starting etherdream communicaiton thread");

        while (!halted)
        {
            try
            {
                if (socket == null || socket.isClosed())
                {
                    connect();
                }
                socket.setSoTimeout(500);

                ByteBuffer buffer = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                boolean endOfStream   = false;
                int     receivedChars = 0;
                try
                {
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
                }
                catch (SocketTimeoutException e)
                {
                    State oldState = state;
                    state = state.stateWhenTimeout(this);
//                    log("Do we have a frame? " + (hasFrame() ? "yes" : "no"));
                    if (oldState != state)
                    {
                        log("State updated from " + oldState + " to " + state);
                        sendCommand();
                    }
                }
                catch (SocketException exception)
                {
                    logException(exception);
                    etherdream.setConnectionFailed();
                    return;
                }
                if (endOfStream)
                {
                    processReply(buffer);
                }

            }
            catch (Exception exception)
            {
                logException(exception);
            }
        }
        if (socket != null)
        {
            try
            {
                log("Sending stop command...");
                state = State.STOP;
                sendCommand();
                socket.close();
            }
            catch (IOException e)
            {
                logException(e);
            }
        }
        etherdream.setConnectionFailed();
    }

    public boolean isHalted()
    {
        return halted;
    }

    private void processReply(ByteBuffer buffer) throws IOException
    {
        try
        {
            EtherdreamResponse response = processResponse(buffer.array());
            lastResponse = response;
            EtherdreamResponseStatus status = EtherdreamResponseStatus.get(response.getResponse().state);
            state = status == EtherdreamResponseStatus.ACK ? state.stateWhenAck(this) : state.stateWhenNak(this);
            if (status != EtherdreamResponseStatus.ACK)
            {
                log("We got a NAK!");
                log(response.toString());
            }
//                        if (oldState != state) {
//                            log("State updated from " + oldState + " to " + state);
//                        }
            if (halted)
            {
                //Somebody could have requested a stop during previous steps
                state = State.STOP;
            }
            sendCommand();
        }
        catch (IllegalStateException e)
        {
            logException(e);
        }
    }

    public void project(List<IldaPoint> points, int pps)
    {
        this.targetPps = pps;
        this.nextFrame = points == null || points.isEmpty() ? null : new CopyOnWriteArrayList<>(points);
    }

    public void halt() throws IOException
    {
        log("Requested to halt");
        halted = true;
    }

    public EtherdreamResponse getLastResponse()
    {
        return lastResponse;
    }

    private boolean hasFrame()
    {
        return nextFrame != null && !nextFrame.isEmpty();
    }

    private EtherdreamResponse processResponse(byte[] array)
    {
        return new EtherdreamResponse(array);
    }

    private List<IldaPoint> getCurrentFrameAndClear()
    {
        currentFrame = nextFrame;
        return currentFrame;
    }

    private void connect() throws IOException
    {
        socket = new Socket(address, 7765);
        socket.setSoTimeout(5000);
        output = socket.getOutputStream();
        input = socket.getInputStream();
    }

    private void sendCommand() throws IOException
    {
        EtherdreamCommand messageToSend = state.generateMessage(this);
        if (messageToSend != null)
        {
            output.write(messageToSend.getBytes());
            output.flush();
        }
    }

    private boolean willNextFrameOverflowBuffer()
    {
        int bufferFullness = lastResponse.getStatus().getBufferFullness();
        return currentFrame != null && currentFrame.size() >= maxBufferSize - bufferFullness;
    }

    enum State
    {
        INIT
            {
                @Override
                State stateWhenAck(EtherdreamCommunicationThread thread)
                {
                    return thread.hasFrame() ? CHECK_STATUS : INIT;
                }

                @Override
                State stateWhenNak(EtherdreamCommunicationThread thread)
                {
                    return thread.hasFrame() ? CHECK_STATUS : INIT;
                }

                @Override
                EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
                {
                    return null;
                }

                @Override
                State stateWhenTimeout(EtherdreamCommunicationThread thread)
                {
                    return thread.hasFrame() ? CHECK_STATUS : INIT;
                }
            },
        CHECK_STATUS
            {
                @Override
                State stateWhenAck(EtherdreamCommunicationThread thread)
                {
                    if (!thread.hasFrame())
                    {
                        return STOP;
                    }
                    if (PLAYING == thread.lastResponse.getStatus().getPlaybackState())
                    {
                        if (thread.willNextFrameOverflowBuffer())
                        {
                            return CHECK_STATUS;
                        }
                        else
                        {
                            return SEND_DATA;
                        }
                    }
                    return PREPARE_STREAM;
                }

                @Override
                State stateWhenNak(EtherdreamCommunicationThread thread)
                {
                    return STOP;
                }

                @Override
                EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
                {
                    return new EtherdreamPingCommand();
                }
            }, PREPARE_STREAM
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread thread)
            {
                return SET_POINT_RATE;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
            {
                return new EtherdreamPrepareStreamCommand();
            }
        }, STOP
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread thread)
            {
                return INIT;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread thread)
            {
                return INIT;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
            {
                return new EtherdreamStopCommand();
            }
        }, SET_POINT_RATE
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread thread)
            {
                return SEND_DATA;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
            {
                return new EtherdreamPointRateCommand(thread.targetPps);
            }
        }, SEND_DATA
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread thread)
            {
                if (PREPARED == thread.lastResponse.getStatus().getPlaybackState())
                {
                    return PROJECT;
                }
                if (thread.willNextFrameOverflowBuffer())
                {
//                    log("Overflow imminent");
                    // Keep checking status until buffer no longer full
                    return CHECK_STATUS;
                }
                return SEND_DATA;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread thread)
            {
                EtherdreamResponse response = thread.lastResponse;
                if (response != null)
                {
                    if (PLAYING == response.getStatus().getPlaybackState())
                    {
                        return SEND_DATA;
                    }
                    else
                    {
                        return thread.hasFrame() ? CHECK_STATUS
                            : STOP;
                    }
                }
                return thread.hasFrame() ? CHECK_STATUS
                    : STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
            {
                List<IldaPoint> frame     = thread.getCurrentFrameAndClear();
                List<IldaPoint> copy      = new ArrayList<>(frame);
                int             frameSize = frame.size();
                //float           pointRate = thread.lastResponse.getStatus().getPointRate();

                int fullness = thread.lastResponse.getStatus().getBufferFullness();
                while (frameSize != 0 && fullness + frameSize < 500)
                {
                    // Arbitrary check to try to keep the buffer from running out
                    copy.addAll(frame);
                    log("Sending the frame twice to avoid underflow, sending " + copy.size() + " points.");
                    frameSize = copy.size();
                }
//                if (pointRate != 0) {
//                    log("Sending a frame consisting of " + frameSize + " points. At " + pointRate + " pps, it should "
//                        + "take " + 1000 * (frameSize / pointRate) + " ms.");
//                }
                return new EtherdreamWriteDataCommand(copy);
            }
        }, PROJECT
        {
            @Override
            State stateWhenAck(EtherdreamCommunicationThread thread)
            {
                return thread.hasFrame() ? SEND_DATA
                    : STOP;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread thread)
            {
                return STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread)
            {
                return new EtherdreamBeginPlaybackCommand(30000);
            }
        };

        abstract State stateWhenAck(EtherdreamCommunicationThread thread);

        abstract State stateWhenNak(EtherdreamCommunicationThread thread);

        State stateWhenTimeout(EtherdreamCommunicationThread thread)
        {
            return this;
        }

        abstract EtherdreamCommand generateMessage(EtherdreamCommunicationThread thread);
    }

}
