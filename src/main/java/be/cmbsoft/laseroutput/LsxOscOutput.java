package be.cmbsoft.laseroutput;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.transport.OSCPortOut;
import ilda.IldaPoint;
import processing.core.PApplet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

/**
 * @author Florian Created on 27/01/2020
 */
public class LsxOscOutput extends LaserOutput {
    public static final String DEFAULT_ROOT_NAME = "LSX_0";
    private final ByteBuffer b;
    private final String ip;
    private final int port;
    private int timeline;
    private int destinationFrame;
    private OSCPortOut outputPort;
    private String rootName = DEFAULT_ROOT_NAME;

    public LsxOscOutput(int timeline, int destinationFrame, String ip, int port) {
        this.timeline = timeline;
        this.destinationFrame = destinationFrame;
        this.ip = ip;
        this.port = port;
        b = ByteBuffer.allocate(45068); //largest point count LSX can handle is 4096
        b.order(ByteOrder.LITTLE_ENDIAN);
        setName("LSX OSC output");
    }

    public static float map(float value, float start1, float stop1, float start2, float stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    @Override
    public synchronized void project(List<IldaPoint> points) {
        int pointCount = points.size();
        b.position(0);

        // LSX frame OSC message
        //HEADER

        b.put((byte) 2);         //type: 0=XYRGB; 1=XYZRGB; 2=XYZPPrRGB
        b.put((byte) 1);         //store: 0 = buffer, 1 = store in frame
        b.put((byte) timeline);  //scanner/timeline
        b.put((byte) 0);         //future

        b.putShort((short) destinationFrame);
        b.putShort((short) pointCount);
        b.putShort((short) 0); //start point
        b.putShort((short) pointCount);

        int max = 32767;
        for (int i = 0; i < Math.min(pointCount, 4096); i++) {
            IldaPoint point = points.get(i);
            if (point == null) {
                // Suspicious! Points shouldn't be null here. This could mean a concurrency issue and flickering output.
                continue;
            }
            short x = (short) PApplet.constrain(map(point.getPosition().x, -1, 1, -max, max), -max, max);
            b.putShort(x);


            short y = (short) PApplet.constrain(map(point.getPosition().y, -1, 1, -max, max), -max, max);
            b.putShort(y);


            short z = (short) PApplet.constrain(map(point.getPosition().z, -1, 1, -max, max), -max, max);
            b.putShort(z);

            // Palette byte:
            //    First bit: normal vector    1 = regular point    0 = normal vector
            //    Second bit: blanking        1 = blanked          0 = unblanked
            //    Third to eighth bit: palette idx (0-63)
            b.put((byte) (1 << 7 | (point.isBlanked() ? 1 << 6 : 0)));

            // Parts-Repeats byte
            //    First to fourth bit: parts (0-15)
            //    Fifth to eighth bit: repeats (0-15)
            b.put((byte) 0);


            int red = (point.getColour() >> 16) & 0xFF;
            int green = (point.getColour() >> 8) & 0xFF;
            int blue = (point.getColour() & 0xFF);

            if (point.isBlanked()) {
                red = 0;
                green = 0;
                blue = 0;
            }

            b.put((byte) red);
            b.put((byte) green);
            b.put((byte) blue);
        }

        OSCMessage message = new OSCMessage("/" + getRootName() + "/Frame", Collections.singletonList(b.array()));

        OSCPortOut oscPort = getOscPort();
        try {
            oscPort.send(message);
        } catch (IOException | OSCSerializeException exception) {
            throw new RuntimeException(exception);
        }
        b.clear();
    }

    private OSCPortOut getOscPort() {
        try {
            if (outputPort == null) {
                outputPort = new OSCPortOut(new InetSocketAddress(ip, port));
            }
            if (!outputPort.isConnected()) {
                outputPort.connect();
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        return outputPort;
    }

    public int getTimeline() {
        return timeline;
    }

    public void setTimeline(int timeline) {
        this.timeline = timeline;
    }

    public int getDestinationFrame() {
        return destinationFrame;
    }

    public void setDestinationFrame(int destinationFrame) {
        this.destinationFrame = destinationFrame;
    }

    public String getRootName() {
        return rootName == null ? rootName = DEFAULT_ROOT_NAME : rootName;
    }

    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

}
