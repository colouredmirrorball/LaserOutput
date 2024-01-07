package kaszagyerek;
/* LICENSE: BSD 2-Clause "Simplified" License
Copyright 2021 Xerxes Rånby

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * Etherdream DAC for Java and Processing 4 
 * By Xerxes Rånby 2021
 * 
 * Minimalistic implementation of the ether-dream protocol
 * https://ether-dream.com/protocol.html
 * 
 * The code automatically discover the DAC by listeneing for the UDP broadcast
 * The statemachine connects and maintains the connection with the DAC using TCP
 * Connection is restarted automatically on connection loss.
 * 
 * 
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Etherdream implements Runnable
{

    enum State
    {
        STARTUP, GET_BROADCAST, INIT, WRITE_DATA
    }

    enum Command
    {
        PREPARE_STREAM('p'), BEGIN_PLAYBACK('b'), QUEUE_RATE_CHANGE('q'), WRITE_DATA('d'), STOP('s'),
        EMERGENCY_STOP(0x00), EMERGENCY_STOP_ALTERNATIVE(0xFF), CLEAR_EMERGENCY_STOP('c'),

        PING('?'), VERSION('v'),

        ACK_RESPONSE('a'), NAK_FULL_RESPONSE('F'), NAK_INVALID_RESPONSE('i'), NAK_STOPCONDITION_RESPONSE('!');

        final byte command;

        Command(int cmd)
        {
            command = (byte) (cmd & 0xFF);
        }

        public byte[] bytes()
        {
            return new byte[]{command};
        }

        public byte[] bytes(int... data)
        {
            switch (this) {
                case BEGIN_PLAYBACK:
                    // 62000060090000
                    return concat(bytes(), toBytesShort(data[0]), toBytes(data[1]));
                default:
                    return concat(bytes(), toBytesShort(data));
            }

        }

        public byte[] bytes(DACPoint p)
        {
            return concat(bytes(), toBytes((short) 1), p.bytes());
        }

        public byte[] bytes(DACPoint[] p)
        {

            byte[] pb = concat(p);
            return concat(bytes(), toBytes((short) p.length), pb);
        }

        public byte getCommand()
        {
            return command;
        }
    }

    enum DACStatus
    {
        IDLE(0x01), PREPARED(0x02), PLAYING(0x03);

        final byte status;

        DACStatus(int cmd)
        {
            status = (byte) (cmd & 0xFF);
        }

        public byte[] bytes()
        {
            return new byte[]{status};
        }
    }

    /* Interface Bytable allows concat to be used for
     * the DACPoint class
     */
    interface Byteable
    {
        byte[] bytes();
    }

    public class DACPoint implements Byteable
    {
        byte[] p;

        DACPoint()
        {
            p = new byte[18];
        }

        DACPoint(int x, int y, int r, int g, int b)
        {
            p = toBytes((char) 0x0, (char) x, (char) y, (char) r, (char) g, (char) b, (char) 0x0, (char) 0x0,
                (char) 0x0);
        }

        DACPoint(int x, int y)
        {
            p = toBytes((char) 0x0, (char) x, (char) y, (char) 65536, (char) 65536, (char) 65536, (char) 0x0,
                (char) 0x0, (char) 0x0);
        }

        /*
         * struct dac_point { uint16_t control; int16_t x; int16_t y; uint16_t r;
         * uint16_t g; uint16_t b; uint16_t i; uint16_t u1; uint16_t u2; };
         */
        public byte[] bytes()
        {
            return p;
        }
    }

    class DACBroadcast
    {
        /*
         * struct j4cDAC_broadcast { uint8_t mac_address[6]; uint16_t hw_revision;
         * uint16_t sw_revision; uint16_t buffer_capacity; uint32_t max_point_rate;
         * struct dac_status status; };
         */

        public final byte[] mac_address;
        public final int
            /* uint16_t */   hw_revision, sw_revision, buffer_capacity, max_point_rate;

        DACBroadcast(byte[] dac_broadcast)
        {
            final ByteBuffer bb = ByteBuffer.wrap(dac_broadcast);
            bb.order(ByteOrder.LITTLE_ENDIAN);

            mac_address = new byte[]{bb.get(), bb.get(), bb.get(), bb.get(), bb.get(), bb.get()};
            /* uint16_t */
            hw_revision = bb.getShort() & 0xFFFF;
            sw_revision = bb.getShort() & 0xFFFF;
            buffer_capacity = bb.getShort() & 0xFFFF;
            max_point_rate = bb.getShort() & 0xFFFF;
        }

        public String toString()
        {
            return " hw_revision: " + hw_revision + " sw_revision: " + sw_revision + "\n buffer_capacity: "
                + buffer_capacity + " max_point_rate: " + max_point_rate;
        }
    }

    class DACResponse
    {
        /*
         * struct dac_response { uint8_t response; uint8_t command; struct status
         * dac_status; };
         *
         * struct dac_status { uint8_t protocol; uint8_t light_engine_state; uint8_t
         * playback_state; uint8_t source; uint16_t light_engine_flags; uint16_t
         * playback_flags; uint16_t source_flags; uint16_t buffer_fullness; uint32_t
         * point_rate; uint32_t point_count; };
         */
        public final int
            /* uint8_t  */   response, command, protocol, light_engine_state, playback_state, source, /* uint16_t */
    light_engine_flags, playback_flags, source_flags, buffer_fullness, /* uint32_t */   point_rate, point_count;


        DACResponse(byte[] dac_response)
        {
            final ByteBuffer bb = ByteBuffer.wrap(dac_response);
            bb.order(ByteOrder.LITTLE_ENDIAN);

            response = bb.get() & 0xFF;
            command = bb.get() & 0xFF;
            /* uint8_t  */
            protocol = bb.get() & 0xFF;
            light_engine_state = bb.get() & 0xFF;
            playback_state = bb.get() & 0xFF;
            source = bb.get() & 0xFF;
            /* uint16_t */
            light_engine_flags = bb.getShort() & 0xFFFF;
            playback_flags = bb.getShort() & 0xFFFF;
            source_flags = bb.getShort() & 0xFFFF;
            buffer_fullness = bb.getShort() & 0xFFFF;
            /* uint32_t */
            point_rate = bb.getInt();
            point_count = bb.getInt();
        }

        public String toString()
        {
            return " light_engine state: " + light_engine_state + " playback state: " + playback_state
                + " buffer_fullness: " + buffer_fullness + " point rate: " + point_rate + " point count: "
                + point_count;
        }
    }
    volatile State state = State.GET_BROADCAST;
    OutputStream output = null;
    InputStream input = null;

    public Etherdream()
    {
        final Thread thread = new Thread(this);
        thread.start();
    }

    /* Helperfunctions to convert arrays and multiarguments of ...
     * to LITTLE_ENDIAN byte[] format used by the Etherdream DAC
     * inspired by superbobs ByteUtil https://gist.github.com/superbob/6548493
     */
    public static byte[] toBytes(final char... charArray)
    { //1 5 5 6 1
        final ByteBuffer bb = ByteBuffer.allocate((charArray.length * 2));
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (final char val: charArray) {
            bb.putChar(val);
        }
        return bb.array();
    }

    public static byte[] toBytes(final int... intArray)
    {
        final ByteBuffer bb = ByteBuffer.allocate((intArray.length * 4));
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (final int val: intArray) {
            bb.putInt(val);
        }
        return bb.array();
    }

    public static byte[] toBytesShort(final int... intArray)
    {
        final ByteBuffer bb = ByteBuffer.allocate((intArray.length * 2));
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (final int val: intArray) {
            bb.putShort((short) val);
        }
        return bb.array();
    }

    public static byte[] toBytes(final short... shortArray)
    {
        final ByteBuffer bb = ByteBuffer.allocate((shortArray.length * 2));
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (final short val: shortArray) {
            bb.putShort(val);
        }
        return bb.array();
    }

    public static void main(String[] args)
    {
        Etherdream laser = new Etherdream();
        while (true) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run()
    {
        State lastState = State.STARTUP;
        DACBroadcast dacBroadcast = null;
        InetAddress etherdreamAddress;
        Socket socket = null;
        DACPoint[] frame = null;

        while (true) {
            if (lastState != state) {
                System.out.println("state: " + lastState + " -> " + state);
                lastState = state;
            }
            try {
                switch (state) {
                    case GET_BROADCAST: {
                        // Wait and get broadcast using UDP

                        try (DatagramSocket inSocket = new DatagramSocket(7654)) {

                            // get broadcast
                            byte[] buffer = new byte[512];
                            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                            inSocket.receive(response);

                            dacBroadcast = new DACBroadcast(Arrays.copyOfRange(buffer, 0, response.getLength()));
                            System.out.println(dacBroadcast);
                            etherdreamAddress = response.getAddress();

                            if (etherdreamAddress != null) {
                                if (socket != null) {
                                    try {
                                        socket.close();
                                        socket = null;
                                    } catch (IOException e1) {
                                    }
                                }
                                socket = new Socket(etherdreamAddress, 7765);

                                output = socket.getOutputStream();
                                input = socket.getInputStream();
                                state = State.INIT;

                                // When a host first connects to the device, the device immediately sends it a
                                // status reply, as if the host had sent a ping packet
                                readResponse(Command.PING);
                            }

                        }

                        break;
                    }
                    case INIT: {
                        // Send ping using TCP port 7765
                        DACResponse r = write(Command.PING);
                        System.out.println(r);

                        write(Command.VERSION);

                        if (r.light_engine_state == 3) {
                            write(Command.CLEAR_EMERGENCY_STOP);
                        }

                        write(Command.PREPARE_STREAM);

                        System.out.println("Filling initial buffer");
                        frame = getFrame();
                        write(Command.WRITE_DATA, frame);
                        frame = getFrame(); // buffer next frame

                        write(Command.BEGIN_PLAYBACK, 0, dacBroadcast.max_point_rate);
                        state = State.WRITE_DATA;
                        break;
                    }
                    case WRITE_DATA: {
                        DACResponse r = write(Command.PING);
                        if (r.buffer_fullness < (dacBroadcast.buffer_capacity - frame.length)) {
                            System.out.println(r);
                            write(Command.WRITE_DATA, frame);
                            frame = getFrame(); // buffer next frame
                        }
                        break;
                    }
                    default:
                        state = State.GET_BROADCAST;
                }
            } catch (Exception e) {
                /*
                 * If any IO error occour for any reason such as network cable disconnect then
                 * try locate the Etherdream DAC again
                 */

                state = State.GET_BROADCAST;
                e.printStackTrace();
            }

        }
    }

    /* Wayne Uroda's byte[] concat
     * https://stackoverflow.com/questions/5513152/easy-way-to-concatenate-two-byte-arrays/12141556#12141556
     */
    static byte[] concat(byte[]... arrays)
    {
        // Determine the length of the result array
        int totalLength = 0;
        for (byte[] bytes: arrays) {
            totalLength += bytes.length;
        }

        // create the result array
        byte[] result = new byte[totalLength];

        // copy the source arrays into the result array
        int currentIndex = 0;
        for (byte[] array: arrays) {
            System.arraycopy(array, 0, result, currentIndex, array.length);
            currentIndex += array.length;
        }

        return result;
    }

    static byte[] concat(Byteable... arrays)
    {
        byte[][] result = new byte[arrays.length][];
        int i = 0;
        for (Byteable a: arrays) {
            result[i] = a.bytes();
            i++;
        }
        return concat(result);
    }

    DACResponse write(Command cmd) throws IOException
    {
        switch (cmd) {
            case PING:
                output.write(cmd.bytes());
                output.flush();
                DACResponse r = readResponse(cmd);
                return r;
            case VERSION:
                output.write(cmd.bytes());
                output.flush();
                byte[] version = input.readNBytes(32);
                String versionString = new String(version).replace("\0", "").strip();
                System.out.println("Version: " + versionString);
                return null;
            default:
                System.out.print("cmd: " + ((char) cmd.command) + " ");
                output.write(cmd.bytes());
                output.flush();
                return readResponse(cmd);
        }
    }

    DACResponse write(Command cmd, int... data) throws IOException
    {
        System.out.print("cmd: " + ((char) cmd.command) + " ");
        byte[] bytes = cmd.bytes(data);
        output.write(bytes);
        output.flush();
        return readResponse(cmd);
    }

    DACResponse write(Command cmd, DACPoint... data) throws IOException
    {
        System.out.print("cmd: " + ((char) cmd.command) + " ");
        DACResponse response = null;

        byte[] bytes = cmd.bytes(data);
        output.write(bytes);
        output.flush();
        response = readResponse(cmd);
        System.out.println("buffered " + response.buffer_fullness);

        return response;

    }

    DACResponse readResponse(Command cmd) throws IOException
    {
        DACResponse dac_response = new DACResponse(input.readNBytes(22));

        //
        if (dac_response.playback_state == 3) {
            System.out.println("E-STOP: " + dac_response.light_engine_flags + " " + dac_response.playback_flags);
            System.out.println(((char) cmd.command) + " " + dac_response);
        }

        // make sure we got an ACK
        if (dac_response.response != Command.ACK_RESPONSE.command) {
            System.out.println("Unexpected response: " + ((char) dac_response.response));
            state = State.GET_BROADCAST;
        }

        // make sure we got the response for current command
        if (dac_response.command != cmd.command) {
            if (dac_response.command == Command.EMERGENCY_STOP.command) {
                System.out.println("E-STOP: " + dac_response.light_engine_flags);
                System.out.println(((char) cmd.command) + " " + dac_response);
            } else {
                System.out.println("Unexpected response from wrong command: " + ((char) dac_response.command));
                System.out.println(((char) cmd.command) + " " + dac_response);
            }
            state = State.GET_BROADCAST;
        }

        return dac_response;
    }

    DACPoint[] getFrame()
    {
        DACPoint[] result = new DACPoint[600];

        for (int i = 0; i < 600; i++) {
            int x;
            int y;
            /* x,y   int min -32767 to max 32767
             * r,g,b int min 0 to max 65535
             *
             * NOTE: TTL r,g,b transistors float from ~26100 to ~26800 up to ~27400
             * this can be used as a hack to output reduced  on
             * when using undimmable TTL laser driver boards
             *
             * 26800, 26800, 27900  all dimmed  white
             * 26200,     0,     0  only dimmed red
             *     0, 26500,     0  only dimmed green
             *     0,     0, 27400  only dimmed blue
             */
            int max = 10767;
            int middle = 5767;

            x = 0;
            y = max;
            if (i > 50) {
                x = middle;
                y = 0;
            }
            if (i > 100) {
                x = max;
                y = 0;
            }
            if (i > 150) {
                x = middle;
                y = -middle;
            }
            if (i > 200) {
                x = max;
                y = -max;
            }
            if (i > 250) {
                x = 0;
                y = -middle;
            }

            if (i > 300) {
                x = -max;
                y = -max;
            }

            if (i > 350) {
                x = -middle;
                y = -middle;
            }

            if (i > 400) {
                x = -max;
                y = 0;
            }

            if (i > 450) {
                x = -middle;
                y = 0;
            }


            float angle = System.nanoTime() / 1000050000.0f;

            // x = (int)((x * Math.cos(angle))-(y*Math.sin(angle)));

            // y = (int)((x * Math.sin(angle))+(y*Math.cos(angle)));
            double cx = 0;
            double cy = 0;
            double s = Math.sin(angle);
            double c = Math.cos(angle);
            // translate point back to origin:
            x -= cx;
            y -= cy;
            // rotate point
            double Xnew = x * c - y * s;
            double Ynew = x * s + y * c;
            // translate point back:
            x = (int) (Xnew + cx);
            y = (int) (Ynew + cy);


            result[i] = new DACPoint(x, y, 62400, 0, 0);
        }
        return result;
    }
}