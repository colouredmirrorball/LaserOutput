package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import be.cmbsoft.ilda.IldaPoint;

import static be.cmbsoft.laseroutput.etherdream.Etherdream.log;
import static be.cmbsoft.laseroutput.etherdream.Etherdream.logException;
import static be.cmbsoft.laseroutput.etherdream.EtherdreamPlaybackState.PLAYING;
import static be.cmbsoft.laseroutput.etherdream.EtherdreamPlaybackState.PREPARED;
import static processing.core.PApplet.hex;
import static processing.core.PApplet.parseChar;

public class EtherdreamCommunicationThread3 extends Thread
{
    private final InetAddress     address;
    private final Etherdream      etherdream;
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

                @Override
                State stateWhenTimeout(EtherdreamCommunicationThread3 thread)
                {
                    return thread.hasFrame() ? CHECK_STATUS : INIT;
                }
            },
        CHECK_STATUS
            {
                @Override
                State stateWhenAck(EtherdreamCommunicationThread3 thread)
                {
                    if (!thread.hasFrame()) {
                        return STOP;
                    }
                    if (PLAYING == thread.lastResponse.getStatus().getPlaybackState()) {
                        if (thread.willNextFrameOverflowBuffer()) {
                            return CHECK_STATUS;
                        } else {
                            return SEND_DATA;
                        }
                    }
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
                if (PREPARED == thread.lastResponse.getStatus().getPlaybackState()) {
                    return PROJECT;
                }
                if (thread.willNextFrameOverflowBuffer()) {
                    log("Overflow imminent");
                    // Keep checking status until buffer no longer full
                    return CHECK_STATUS;
                }
                return SEND_DATA;
            }

            @Override
            State stateWhenNak(EtherdreamCommunicationThread3 thread)
            {
                EtherdreamResponse response = thread.lastResponse;
                if (response != null) {
                    if (PLAYING == response.getStatus().getPlaybackState()) {
                        return SEND_DATA;
                    } else {
                        return thread.hasFrame() ? CHECK_STATUS : STOP;
                    }
                }
                return thread.hasFrame() ? CHECK_STATUS : STOP;
            }

            @Override
            EtherdreamCommand generateMessage(EtherdreamCommunicationThread3 thread)
            {
                List<IldaPoint> frame     = thread.getCurrentFrameAndClear();
                List<IldaPoint> copy      = new ArrayList<>(frame);
                int             frameSize = frame.size();
                float           pointRate = thread.lastResponse.getStatus().getPointRate();


                int fullness = thread.lastResponse.getStatus().getBufferFullness();
                while (fullness + frameSize < 500)
                {
                    // Arbitrary check to try to keep the buffer from running out
                    copy.addAll(frame);
                    log("Sending the frame twice to avoid underflow, sending " + copy.size() + " points.");
                    frameSize = copy.size();
                }
                if (pointRate != 0) {
                    log("Sending a frame consisting of " + frameSize + " points. At " + pointRate + " pps, it should "
                        + "take " + 1000 * (frameSize / pointRate) + " ms.");
                }
                return new EtherdreamWriteDataCommand(copy);
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

    private final int                maxBufferSize;
    private       boolean            halted;
    private       Socket             socket;
    private       OutputStream       output;
    private       InputStream        input;
    private       int                targetPps;
    private       List<IldaPoint>    nextFrame;
    private       List<IldaPoint>    currentFrame;
    private       State              state           = State.INIT;
    private       EtherdreamResponse lastResponse;
    private       long               lastCommandTime = System.currentTimeMillis();
    private       EtherdreamCommand  previousMessage = null;

    EtherdreamCommunicationThread3(InetAddress address, Etherdream etherdream)
    {
        setName("EtherdreamCommunicationThread");
        this.address       = address;
        this.etherdream    = etherdream;
        this.maxBufferSize = etherdream.getBroadcast().getBufferCapacity();
    }

    public boolean isHalted()
    {
        return halted;
    }

    @Override
    public void run()
    {
        log("starting etherdream communicaiton thread");

        while (!halted) {
            try {
                if (socket == null || socket.isClosed()) {
                    connect();
                }
                socket.setSoTimeout(500);

                ByteBuffer buffer = ByteBuffer.allocate(22);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                boolean endOfStream   = false;
                int     receivedChars = 0;
                try {
                    while (!endOfStream) {
                        // Blocks until we received an input from the Etherdream
                        int b = input.read();
                        if (b < 0 || ++receivedChars >= buffer.capacity()) {
                            endOfStream = true;
                        } else {
                            buffer.put((byte) (b & 0xff));
                        }
                    }
                } catch (SocketTimeoutException e) {
                    State oldState = state;
                    state = state.stateWhenTimeout(this);
                    log("Do we have a frame? " + (hasFrame() ? "yes" : "no"));
                    if (oldState != state) {
                        log("State updated from " + oldState + " to " + state);
                        sendCommand();
                    }
                }
                if (endOfStream) {
                    try {
                        EtherdreamResponse response = processResponse(buffer.array());
                        lastResponse = response;
                        log(response.toString());
                        EtherdreamResponseStatus status   = EtherdreamResponseStatus.get(response.getResponse().state);
                        State                    oldState = state;
                        state = status == EtherdreamResponseStatus.ACK ? state.stateWhenAck(this)
                            : state.stateWhenNak(this);
                        if (status != EtherdreamResponseStatus.ACK) {
                            StringJoiner joiner = new StringJoiner(" - ");
                            for (byte b: previousMessage.getBytes()) {

                                joiner.add("0x" + hex(b) + "|" + parseChar(b));
                            }
                            log("Underflow? " + response.getStatus().getPlaybackFlags().isUnderFlow());
                            System.out.println(joiner);
                        }
                        if (oldState != state) {
                            log("State updated from " + oldState + " to " + state);
                        }
                        if (halted) {
                            //Somebody could have requested a stop during previous steps
                            state = State.STOP;
                        }
                        sendCommand();
                    } catch (IllegalStateException e) {
                        logException(e);
                    }
                }

            } catch (Exception exception) {
                logException(exception);
            }
        }
        if (socket != null) {
            try {
                log("Sending stop command...");
                state = State.STOP;
                sendCommand();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        etherdream.setConnectionFailed();
    }

    public void project(List<IldaPoint> points, int pps)
    {
        this.targetPps = pps;
        this.nextFrame = points == null || points.isEmpty() ? null : points;
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

    private void connect() throws IOException
    {
        socket = new Socket(address, 7765);
        socket.setSoTimeout(5000);
        output = socket.getOutputStream();
        input  = socket.getInputStream();
    }

    private List<IldaPoint> getCurrentFrameAndClear()
    {
        currentFrame = nextFrame;
        return currentFrame;
    }

    private void sendCommand() throws IOException
    {
        EtherdreamCommand messageToSend = state.generateMessage(this);
        if (messageToSend != null) {
            long now = System.nanoTime();
            log("Sending command " + messageToSend.getCommandChar() + ", we waited "
                + (now - lastCommandTime) / 1000000f + " ms for this.");
            previousMessage = messageToSend;
            output.write(messageToSend.getBytes());
            output.flush();
            lastCommandTime = now;
        }
    }

    private boolean willNextFrameOverflowBuffer()
    {
        int bufferFullness = lastResponse.getStatus().getBufferFullness();
        return currentFrame.size() > maxBufferSize - bufferFullness;
    }

}
