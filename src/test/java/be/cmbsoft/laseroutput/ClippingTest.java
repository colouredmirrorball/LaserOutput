package be.cmbsoft.laseroutput;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.ilda.IldaRenderer;
import be.cmbsoft.ilda.OptimisationSettings;
import be.cmbsoft.ilda.Optimiser;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClippingTest
{
    @Test
    void clip()
    {
        /*
        4 points in this configuration:
           _o_
          |   |
        o |   |o
          |___|
            o
         */

        IldaPoint first  = new IldaPoint(0, 1.25f, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(1.25f, 0, 0, 255, 255, 255, false);
        IldaPoint third  = new IldaPoint(0, -1.25f, 0, 255, 255, 255, false);
        IldaPoint fourth = new IldaPoint(-1.25f, 0, 0, 255, 255, 255, false);
        IldaPoint fifth  = new IldaPoint(0, 1.25f, 0, 255, 255, 255, false);
        IldaPoint sixth  = new IldaPoint(0, 0, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first, second, third, fourth, fifth, sixth));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> pointList = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(pointList));

    }

    @Test
    void clip2()
    {

        IldaPoint first  = new IldaPoint(0, 0, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(-0.25f, 0.25f, 0, 255, 255, 255, false);
        IldaPoint third  = new IldaPoint(-1.25f, 0.f, 0, 255, 255, 255, false);
        IldaPoint fourth = new IldaPoint(0.5f, -1.25f, 0, 255, 255, 255, false);
        IldaPoint fifth  = new IldaPoint(1f, -1.25f, 0, 255, 255, 255, false);
        IldaPoint sixth  = new IldaPoint(0.25f, -0.25f, 0, 255, 255, 255, false);


        List<IldaPoint> points = new ArrayList<>(List.of(first, second, third, fourth, fifth, sixth));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clip3()
    {

        IldaPoint fifth = new IldaPoint(1f, -1.25f, 0, 255, 255, 255, false);
        IldaPoint sixth = new IldaPoint(0.25f, -0.25f, 0, 255, 255, 255, false);


        List<IldaPoint> points = new ArrayList<>(List.of(fifth, sixth));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }


    @Test
    void clipInToOutCanvas()
    {

        IldaPoint first  = new IldaPoint(0, 0, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(-1.25f, 0.25f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first, second));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipOutCanvas()
    {

        IldaPoint first  = new IldaPoint(-1.2f, 0.4f, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(-1.25f, 0.25f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first, second));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipOutToInCanvas()
    {

        IldaPoint first  = new IldaPoint(-1.2f, 0.4f, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(-0.25f, 0.25f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first, second));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipSinglePoint()
    {

        IldaPoint first = new IldaPoint(-0.2f, 0.4f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        assertEquals(1, finalPoints.size());

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipSinglePointOutside()
    {

        IldaPoint first = new IldaPoint(-1.2f, 0.4f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        assertEquals(0, finalPoints.size());

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipTwoPointsOutside()
    {

        IldaPoint first  = new IldaPoint(-1.2f, 0.4f, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(-1.4f, 0.6f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first, second));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        assertEquals(0, finalPoints.size());

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipTwoPointsOutsideIntersecting()
    {

        IldaPoint first  = new IldaPoint(-1.2f, 0.4f, 0, 255, 255, 255, false);
        IldaPoint second = new IldaPoint(-0.4f, 1.2f, 0, 255, 255, 255, false);

        List<IldaPoint> points = new ArrayList<>(List.of(first, second));

        OptimisationSettings settings = new OptimisationSettings()
            .setClippingEnabled(true)
            .setInterpolateBlanked(false)
            .setInterpolateLit(false);
        Optimiser optimiser = new Optimiser(settings);

        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        assertEquals(2, finalPoints.size());

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(finalPoints));

    }

    @Test
    void clipCircle()
    {

        IldaRenderer renderer = new IldaRenderer(null, 200, 200);
        renderer.beginDraw();
        renderer.stroke(255);
        renderer.ellipse(100, 100, 240, 240);
        renderer.endDraw();
//        List<IldaPoint> points = renderer.getCurrentFrame().getPoints();
//
//        OptimisationSettings settings = new OptimisationSettings()
//            .setClippingEnabled(true)
//            .setInterpolateBlanked(false)
//            .setInterpolateLit(false);
//        Optimiser optimiser = new Optimiser(settings);

//        List<IldaPoint> finalPoints = optimiser.optimiseSegment(points);

        LsxOscOutput output = new LsxOscOutput(1, 10, "127.0.0.1", 10000);
        Assertions.assertDoesNotThrow(() -> output.project(renderer));

    }

}
