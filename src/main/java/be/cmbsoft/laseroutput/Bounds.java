package be.cmbsoft.laseroutput;

import processing.core.PVector;

public class Bounds
{
    float[] xk = new float[4];
    float[] yk = new float[4];
    private PVector upperLeft;
    private PVector upperRight;
    private PVector lowerLeft;
    private PVector lowerRight;

    public PVector getUpperLeft()
    {
        return upperLeft;
    }

    public void setUpperLeft(PVector upperLeft)
    {
        this.upperLeft = upperLeft;
        update();
    }

    public PVector getUpperRight()
    {
        return upperRight;
    }

    public void setUpperRight(PVector upperRight)
    {
        this.upperRight = upperRight;
        update();
    }

    public PVector getLowerLeft()
    {
        return lowerLeft;
    }

    public void setLowerLeft(PVector lowerLeft)
    {
        this.lowerLeft = lowerLeft;
        update();
    }

    public PVector getLowerRight()
    {
        return lowerRight;
    }

    public void setLowerRight(PVector lowerRight)
    {
        this.lowerRight = lowerRight;
        update();
    }

    private void update()
    {
        // TODO: validate (ensure vectors inside window and lower left is actually lower left etc.)
        xk[0] = lowerLeft == null ? -1 : lowerLeft.x;
        xk[1] = lowerRight == null ? 1 : lowerRight.x;
        xk[2] = upperRight == null ? 1 : upperRight.x;
        xk[3] = upperLeft == null ? -1 : upperLeft.x;

        yk[0] = lowerLeft == null ? -1 : lowerLeft.y;
        yk[1] = lowerRight == null ? -1 : lowerRight.y;
        yk[2] = upperRight == null ? 1 : upperRight.y;
        yk[3] = upperLeft == null ? 1 : upperLeft.y;
    }

}
