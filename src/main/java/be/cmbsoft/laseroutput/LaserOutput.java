package be.cmbsoft.laseroutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import be.cmbsoft.ilda.IldaFrame;
import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.ilda.IldaRenderer;
import processing.core.PImage;
import processing.core.PVector;

/**
 * @author Florian Created on 27/01/2020
 */
public abstract class LaserOutput extends Thread
{
    public enum Mode
    {
        STATIC_FPS, STATIC_PPS
    }
    private final List<OutputOption> options              = new ArrayList<>();
    private final Bounds bounds          = new Bounds();
    private       int                pps                  = 30000;
    private       boolean            paused;
    private       int                fps                  = 30;
    private       Mode               mode                 = Mode.STATIC_PPS;
    private       long               millisecondsPerFrame = 1000L / fps;
    private       int                lastFramePointCount  = 0;
    private       boolean            interrupted          = false;
    private       List<IldaPoint>    points               = new ArrayList<>();
    private       float  intensityFactor = 1;
    private       PImage safetyZone      = null;

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
            } catch (InterruptedException exception)
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

    /**
     * Set “master” intensity (0-255)
     *
     * @param intensity
     */
    public void setIntensity(float intensity)
    {
        this.intensityFactor = intensity / 255f;
    }

    public int getCurrentFramePointCount()
    {
        return Optional.ofNullable(points).map(List::size).orElse(0);
    }

    public synchronized void setCurrentFrame(IldaFrame frame)
    {
        this.points = Optional.ofNullable(frame).map(IldaFrame::getPoints).orElse(Collections.emptyList());
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
        } else
        {
            paused = true;
        }
    }

    /**
     * Define a safety zone, also called beam attenuation map. The argument is an image, which will be rescaled to
     * match the output aspect ratio. The blue value of the pixels corresponding to the location of a laser point,
     * will be used to adjust the intensity of the laser at that location.
     * <p>
     * Beware! Only the intensity of the point is adjusted, not the intensity of the path to this point. If two
     * points are being displayed, one at full power outside the defined safety zone and another well inside the
     * zone, the beam will remain at full power along the path until it reaches the second point, even when the path
     * crosses the safety zone boundary.
     *
     * @param safetyZone pixel grid as a PImage where the blue channel attenuates the intensity of the laser projection
     */
    public void setSafetyZone(PImage safetyZone)
    {
        this.safetyZone = safetyZone;
    }

    public abstract void project(List<IldaPoint> points);

    public void project(IldaFrame frame)
    {
        Optional.ofNullable(frame).ifPresent(f -> project(f.getPoints()));
    }

    public void project(IldaRenderer renderer)
    {
        if (!interrupted)
        {
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

    /**
     * The Bounds, or Boundaries, are four vectors that define the quadrilateral figure in which projection occurs.
     * Note that the figure does not need to be regular (rectangular): the image geometry will be transformed
     * accordingly. Only polygons with 4 corners are supported at this moment.
     *
     * @return object containing the corners of the output shape
     */
    public Bounds getBounds()
    {
        return bounds;
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

    private float getSafetyZoneIntensity(IldaPoint point)
    {
        return safetyZone == null ? 1f : (safetyZone.get((int) ((point.getX() + 1) * (safetyZone.width - 1) / 2),
            (int) ((-point.getY() + 1) * (safetyZone.height - 1) / 2)) & 0xff) / 255f;
    }

    private List<IldaPoint> applyBounds(List<IldaPoint> points)
    {

        /*
         * 4-points polynomial mapping algorithm courtesy of Daniele Mortari and David Arnas:
         * Mortari, D.; Arnas, D. Bijective Mapping Analysis to Extend the Theory of Functional Connections to
         * Non-Rectangular 2-Dimensional Domains. Mathematics 2020, 8, 1593. https://doi.org/10.3390/math8091593
         */

        List<IldaPoint> output = new ArrayList<>();
        for (IldaPoint point: points)
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

    protected List<IldaPoint> transform(List<IldaPoint> points)
    {
        for (OutputOption option: options)
        {
            points = option.transform(points);
        }
        for (IldaPoint point: points)
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

}
