package com.bettercontent.spiritcommerce.trader;

import java.util.ArrayList;
import java.util.List;

/** Immutable render-only camp mesh. Local +Z is the trader/customer approach. */
final class TraderCampAwningGeometry {
    static final int COLUMNS = 6;
    static final int ROWS = 5;
    static final float HALF_WIDTH = 0.55F;
    static final float BACK_Z = 0.03F;
    static final float LENGTH = 2.02F;
    static final float BACK_HEIGHT = 2.78F;
    static final float FRONT_HEIGHT = 2.42F;
    static final float SAG = 0.06F;
    static final float THICKNESS = 0.012F;

    static final List<Box> SUPPORTS = List.of(
            new Box(-0.055F, 0, -0.055F, 0.055F, 2.85F, 0.055F),
            new Box(-0.57F, 2.75F, -0.045F, 0.57F, 2.83F, 0.045F),
            new Box(-0.58F, 0, 2.02F, -0.52F, 2.43F, 2.08F),
            new Box(0.52F, 0, 2.02F, 0.58F, 2.43F, 2.08F),
            // Inset beam depth and ends inside the stakes: joints touch without co-facing planes.
            new Box(-0.56F, 2.38F, 2.03F, 0.56F, 2.44F, 2.07F));
    static final List<Quad> CLOTH = cloth();
    static final List<Quad> WOOD = SUPPORTS.stream().flatMap(box -> boxFaces(box).stream()).toList();

    private TraderCampAwningGeometry() { }

    /** Signed right-handed Y rotation: the sheet must follow the native Direction stand target. */
    static float yawFor(final int stepX, final int stepZ) {
        return (float) Math.toDegrees(Math.atan2(stepX, stepZ));
    }

    static Vertex point(final float u, final float v, final boolean underside) {
        float y = BACK_HEIGHT + (FRONT_HEIGHT - BACK_HEIGHT) * v
                - SAG * (float) Math.sin(Math.PI * u) * (float) Math.sin(Math.PI * v);
        return new Vertex(-HALF_WIDTH + 2 * HALF_WIDTH * u, y - (underside ? THICKNESS : 0),
                BACK_Z + LENGTH * v, u, v);
    }

    private static List<Quad> cloth() {
        List<Quad> faces = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            float v0 = row / (float) ROWS;
            float v1 = (row + 1) / (float) ROWS;
            for (int column = 0; column < COLUMNS; column++) {
                float u0 = column / (float) COLUMNS;
                float u1 = (column + 1) / (float) COLUMNS;
                faces.add(new Quad(point(u0, v0, false), point(u0, v1, false),
                        point(u1, v1, false), point(u1, v0, false)));
                // Reverse winding but keep the SAME 00-to-11 triangulation diagonal.
                // Reversing from another corner makes the two warped sheets intersect.
                faces.add(new Quad(point(u0, v0, true), point(u1, v0, true),
                        point(u1, v1, true), point(u0, v1, true)));
            }
            faces.add(new Quad(point(0, v0, false), point(0, v0, true),
                    point(0, v1, true), point(0, v1, false)));
            faces.add(new Quad(point(1, v1, false), point(1, v1, true),
                    point(1, v0, true), point(1, v0, false)));
        }
        for (int column = 0; column < COLUMNS; column++) {
            float u0 = column / (float) COLUMNS;
            float u1 = (column + 1) / (float) COLUMNS;
            faces.add(new Quad(point(u0, 1, false), point(u0, 1, true),
                    point(u1, 1, true), point(u1, 1, false)));
            faces.add(new Quad(point(u1, 0, false), point(u1, 0, true),
                    point(u0, 0, true), point(u0, 0, false)));
        }
        return List.copyOf(faces);
    }

    static List<Quad> boxFaces(final Box box) {
        float x0 = box.x0(), y0 = box.y0(), z0 = box.z0();
        float x1 = box.x1(), y1 = box.y1(), z1 = box.z1();
        return List.of(
                woodFace(x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0),
                woodFace(x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1),
                woodFace(x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0),
                woodFace(x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1),
                woodFace(x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0),
                woodFace(x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1));
    }

    private static Quad woodFace(final float ax, final float ay, final float az,
                                 final float bx, final float by, final float bz,
                                 final float cx, final float cy, final float cz,
                                 final float dx, final float dy, final float dz) {
        // A block-sized texture tile stays block-sized on both narrow and tall timber faces.
        float width = distance(ax, ay, az, bx, by, bz);
        float height = distance(ax, ay, az, dx, dy, dz);
        return new Quad(new Vertex(ax,ay,az,0,height), new Vertex(bx,by,bz,width,height),
                new Vertex(cx,cy,cz,width,0), new Vertex(dx,dy,dz,0,0));
    }

    private static float distance(final float ax, final float ay, final float az,
                                  final float bx, final float by, final float bz) {
        return (float) Math.sqrt((bx-ax)*(bx-ax) + (by-ay)*(by-ay) + (bz-az)*(bz-az));
    }

    record Vertex(float x, float y, float z, float u, float v) { }
    record Normal(float x, float y, float z) { }
    record Box(float x0, float y0, float z0, float x1, float y1, float z1) { }

    record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {
        List<Vertex> vertices() { return List.of(a, b, c, d); }

        Normal normal() {
            float bx = b.x() - a.x(), by = b.y() - a.y(), bz = b.z() - a.z();
            float dx = d.x() - a.x(), dy = d.y() - a.y(), dz = d.z() - a.z();
            float nx = by * dz - bz * dy;
            float ny = bz * dx - bx * dz;
            float nz = bx * dy - by * dx;
            float length = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            return new Normal(nx / length, ny / length, nz / length);
        }
    }
}
