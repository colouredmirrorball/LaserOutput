package be.cmbsoft.laseroutput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import be.cmbsoft.ilda.IldaPoint;

public enum OutputOption
{
    INVERT_X(points ->
    {
        List<IldaPoint> output = new ArrayList<>();
        for (IldaPoint point : points)
        {
            IldaPoint outPoint = new IldaPoint(point);
            outPoint.getPosition().x = -outPoint.getX();
            output.add(outPoint);
        }
        return output;
    }),
    INVERT_Y(points ->
    {
        List<IldaPoint> output = new ArrayList<>();
        for (IldaPoint point : points)
        {
            IldaPoint outPoint = new IldaPoint(point);
            outPoint.getPosition().y = -outPoint.getY();
            output.add(outPoint);
        }
        return output;
    });

    private final UnaryOperator<List<IldaPoint>> transformFunction;

    OutputOption(UnaryOperator<List<IldaPoint>> transformFunction)
    {
        this.transformFunction = transformFunction;
    }

    public List<IldaPoint> transform(List<IldaPoint> points)
    {
        return transformFunction.apply(points);
    }
}
