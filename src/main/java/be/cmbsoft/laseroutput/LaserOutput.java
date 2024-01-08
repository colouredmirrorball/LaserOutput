package be.cmbsoft.laseroutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import be.cmbsoft.ilda.IldaFrame;
import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.ilda.IldaRenderer;

/**
 * @author Florian Created on 27/01/2020
 */
public abstract class LaserOutput extends Thread
{
    private int pps = 30000;
    private boolean paused;
    private int fps = 30;
    private Mode mode = Mode.STATIC_PPS;
    private long millisecondsPerFrame = 1000L / fps;
    private int lastFramePointCount = 0;
    private boolean interrupted = false;
    private List<IldaPoint> points = new ArrayList<>();

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

    public synchronized void setCurrentPoints(List<IldaPoint> points) {
        this.points = points;
    }

    public int getPps()
    {
        return pps;
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

    public LaserOutput setPps(int pps)
    {
        this.pps = pps;
        return this;
    }

    public abstract void project(List<IldaPoint> points);

    public void project(IldaFrame frame) {
        Optional.ofNullable(frame).ifPresent(f -> project(f.getPoints()));
    }

    public void project(IldaRenderer renderer) {
        Optional.ofNullable(renderer)
                .map(IldaRenderer::getCurrentFrame)
                .ifPresent(frame -> this.points = frame.getPoints());
        project(points);
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

    public enum Mode
    {
        STATIC_FPS, STATIC_PPS
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

}
