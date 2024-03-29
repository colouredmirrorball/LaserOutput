package be.cmbsoft.laseroutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import be.cmbsoft.ilda.IldaFrame;
import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.ilda.IldaRenderer;
import processing.core.PGraphics;
import processing.core.PVector;

/**
 * @author Florian Created on 27/01/2020
 */
public abstract class LaserOutput extends Thread
{
    private final List<OutputOption> options              = new ArrayList<>();
    private       int                pps                  = 30000;
    private       boolean            paused;
    private       int                fps                  = 30;
    private       Mode               mode                 = Mode.STATIC_PPS;
    private       long               millisecondsPerFrame = 1000L / fps;
    private       int                lastFramePointCount  = 0;
    private       boolean            interrupted          = false;
    private       List<IldaPoint>    points               = new ArrayList<>();
    private final Bounds             bounds               = new Bounds();
    private       float              intensityFactor      = 1;
    private       PGraphics          safetyZone           = null;

    protected LaserOutput()
    {
        setName("Laser output");
    }

    @Override
    public void run()
    {
        long lastTime = 0L;
        while (!interrupted)
        {
            try
            {
                if (!paused)
                {
                    project(points);
                    lastFramePointCount = points.size();
                }
                long sleepTime = getSleepTime(lastTime, lastFramePointCount);
                lastTime = System.currentTimeMillis();
                sleep(sleepTime);
            }
            catch (InterruptedException exception)
            {
                interrupt();
                interrupted = true;
            }
        }
    }

    public int getIntensity()
    {
        return (int) (intensityFactor * 255);
    }

    private long getSleepTime(long lastTime, int lastFramePointCount)
    {
        long currentTime = System.currentTimeMillis();
        switch (mode)
        {
            case STATIC_FPS:
                return Math.max(0, millisecondsPerFrame - (currentTime - lastTime));
            case STATIC_PPS:
                long allottedFrameDuration = lastFramePointCount == 0 ? 33 : lastFramePointCount / pps;
                return Math.max(0, allottedFrameDuration - (currentTime - lastTime));
            default:
                throw new IllegalStateException("Unexpected value: " + mode);
        }
    }

    public synchronized void setCurrentFrame(IldaFrame frame)
    {
        this.points = Optional.ofNullable(frame)
                              .map(IldaFrame::getPoints)
                              .orElse(Collections.emptyList());
    }

    public synchronized void setCurrentPoints(List<IldaPoint> points)
    {
        this.points = points;
    }

    public int getPps()
    {
        return pps;
    }

    public LaserOutput setPps(int pps)
    {
        this.pps = pps;
        return this;
    }

    public int getFps()
    {
        return fps;
    }

    public void setFps(int fps)
    {
        this.fps = fps;
        if (fps != 0)
        {
            millisecondsPerFrame = 1000L / fps;
        }
        else
        {
            paused = true;
        }
    }

    protected List<IldaPoint> transform(List<IldaPoint> points)
    {
        for (OutputOption option : options)
        {
            points = option.transform(points);
        }
        for (IldaPoint point : points)
        {
            int   colour             = point.getColour();
            float theIntensityFactor = intensityFactor;
            theIntensityFactor *= getSafetyZoneIntensity(point);

            float red   = (colour >> 16) & 0xff;
            float green = (colour >> 8) & 0xff;
            float blue  = (colour) & 0xff;
            point.setColour((int) (red * theIntensityFactor), (int) (green * theIntensityFactor),
                (int) (blue * theIntensityFactor));
        }
        return applyBounds(points);
    }

    private float getSafetyZoneIntensity(IldaPoint point)
    {
        return safetyZone == null ? 1f : (safetyZone.get((int) ((point.getX() + 1) * (safetyZone.width - 1) / 2),
            (int) ((-point.getY() + 1) * (safetyZone.height - 1) / 2)) & 0xff) / 255f;
    }

    public void setSafetyZone(PGraphics safetyZone)
    {
        this.safetyZone = safetyZone;
    }

    private List<IldaPoint> applyBounds(List<IldaPoint> points)
    {

        /*
         * 4-points polynomial mapping algorithm courtesy of Daniele Mortari and David Arnas:
         * Mortari, D.; Arnas, D. Bijective Mapping Analysis to Extend the Theory of Functional Connections to
         * Non-Rectangular 2-Dimensional Domains. Mathematics 2020, 8, 1593. https://doi.org/10.3390/math8091593
         */

        List<IldaPoint> output = new ArrayList<>();
        for (IldaPoint point : points)
        {
            PVector   position = point.getPosition();
            float     newX     = calculateMapX(position.x, position.y);
            float     newY     = calculateMapY(position.x, position.y);
            IldaPoint newPoint = new IldaPoint(point);
            newPoint.setPosition(newX, newY, point.getPosition().z);
            output.add(newPoint);
        }
        return output;
    }

    private float calculateMapX(float a, float b)
    {
        return calculateMap(a, b, bounds.xk);
    }

    private float calculateMapY(float a, float b)
    {
        return calculateMap(a, b, bounds.yk);
    }

    private float calculateMap(float a, float b, float[] xyk)
    {
        float sum = 0;
        for (int k = 0; k < 4; k++)
        {
            sum += xyk[k] * fk(a, b, k);
        }
        return sum;
    }

    private float fk(float a, float b, int k)
    {
        return switch (k)
            {
                case 0 -> (1 - a - b + a * b) * 0.25f;
                case 1 -> (1 + a - b - a * b) * 0.25f;
                case 2 -> (1 + a + b + a * b) * 0.25f;
                case 3 -> (1 - a + b - a * b) * 0.25f;
                default -> throw new IllegalArgumentException();
            };
    }

    public abstract void project(List<IldaPoint> points);

    public void project(IldaFrame frame)
    {
        Optional.ofNullable(frame).ifPresent(f -> project(f.getPoints()));
    }

    public void project(IldaRenderer renderer)
    {
        if (!interrupted) {
            Optional.ofNullable(renderer)
                .map(IldaRenderer::getCurrentFrame)
                .ifPresent(frame -> this.points = frame.getPoints());
            project(points);
        }
    }

    public Mode getMode()
    {
        return mode;
    }

    public LaserOutput setMode(Mode mode)
    {
        this.mode = mode;
        return this;
    }

    public void option(OutputOption option)
    {
        options.add(option);
    }

    public void halt()
    {
        interrupted = true;
    }

    /**
     * This method sends an empty frame, which can be useful to disable output.
     */
    public void sendEmptyFrame()
    {
        project(Collections.emptyList());
    }

    public abstract boolean isConnected();

    public enum Mode
    {
        STATIC_FPS, STATIC_PPS
    }

    /**
     * The Bounds, or Boundaries, are four vectors that define the quadrilateral figure in which projection occurs.
     * Note that the figure does not need to be regular.
     *
     * @return object containing the corners of the output shape
     */
    public Bounds getBounds()
    {
        return bounds;
    }

    public void setIntensity(float intensity)
    {
        this.intensityFactor = intensity / 255f;
    }

}
