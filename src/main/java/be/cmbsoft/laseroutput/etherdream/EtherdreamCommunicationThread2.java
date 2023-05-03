package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import be.cmbsoft.ilda.IldaPoint;

public class EtherdreamCommunicationThread2 extends Thread {

    private final Etherdream etherdream;
    private final InetAddress address;
    private Socket socket;
    private OutputStream output;
    private InputStream input;
    private int targetPps;
    boolean stopped = false;
    private final ArrayBlockingQueue<IldaPoint> points = new ArrayBlockingQueue<>(4000);
    private EtherdreamStatus currentStatus;

    EtherdreamCommunicationThread2(InetAddress address, Etherdream etherdream) {
        setName("EtherdreamCommunicationThread");
        this.address = address;
        this.etherdream = etherdream;
    }

    private void connect() throws IOException {
        socket = new Socket(address, 7765);
        socket.setSoTimeout(5000);
        output = socket.getOutputStream();
        input = socket.getInputStream();
    }

    @Override
    public void run() {
        try {
            connect();

            while (!stopped) {
                readInput();
            }
            sendStopCommand();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void readInput() throws IOException {
        boolean endOfStream = false;
        ByteBuffer buffer = ByteBuffer.allocate(22);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int receivedChars = 0;
        while (!endOfStream)
        {
            int b = input.read();
            if (b < 0 || ++receivedChars >= buffer.capacity())
            {
                endOfStream = true;
            } else
            {
                buffer.put((byte) (b & 0xff));
            }
        }
        processResponse(buffer.array());
    }

    private void processResponse(byte[] array) {
        EtherdreamResponse response = new EtherdreamResponse(array);
        currentStatus = response.getStatus();
    }

    private void sendStopCommand() throws IOException {
        output.write(new EtherdreamStopCommand().getBytes());
        output.flush();
    }

    public void project(List<IldaPoint> points, int pps) {
        this.targetPps = pps;
        this.points.addAll(points);
    }

    public void halt() {
        stopped = true;
    }
}
