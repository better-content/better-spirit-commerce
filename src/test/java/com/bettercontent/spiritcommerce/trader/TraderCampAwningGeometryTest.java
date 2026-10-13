package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class TraderCampAwningGeometryTest {
    @Test
    void wholeClothIncludingUndersideClearsTraderAndCustomerWithRestrainedSag() {
        // |linear slope| > maximum sag derivative, so the true minimum is the front edge.
        assertTrue(TraderCampAwningGeometry.BACK_HEIGHT - TraderCampAwningGeometry.FRONT_HEIGHT
                > TraderCampAwningGeometry.SAG * Math.PI);
        for (int column = 0; column <= 100; column++) {
            float lastY = Float.POSITIVE_INFINITY;
            for (int row = 0; row <= 100; row++) {
                float u = column / 100F, v = row / 100F;
                var p = TraderCampAwningGeometry.point(u, v, true);
                assertTrue(p.y() >= 2.1F, "headroom at " + u + "," + v);
                assertTrue(p.y() >= 2.408F - 0.00001F);
                assertTrue(p.y() <= lastY + 0.00001F);
                lastY = p.y();
                float line = TraderCampAwningGeometry.BACK_HEIGHT
                        + (TraderCampAwningGeometry.FRONT_HEIGHT - TraderCampAwningGeometry.BACK_HEIGHT) * v;
                float top = TraderCampAwningGeometry.point(u, v, false).y();
                assertTrue(line - top >= -0.00001F && line - top <= 0.06001F);
            }
        }
        // The front beam is also overhead, and the two stakes leave a full block-wide corridor.
        assertTrue(TraderCampAwningGeometry.SUPPORTS.get(4).y0() >= 2.1F);
        assertTrue(TraderCampAwningGeometry.SUPPORTS.get(2).x1() <= -0.5F);
        assertTrue(TraderCampAwningGeometry.SUPPORTS.get(3).x0() >= 0.5F);
    }

    @Test
    void stakesHaveRealVolumeAndClothCornersConnectToTheirSupports() {
        for (var box : TraderCampAwningGeometry.SUPPORTS) {
            assertTrue(box.x1() > box.x0());
            assertTrue(box.y1() > box.y0());
            assertTrue(box.z1() > box.z0());
        }
        for (boolean underside : List.of(false, true)) {
            for (float u : new float[]{0, 1}) {
                assertInside(TraderCampAwningGeometry.SUPPORTS.get(1),
                        TraderCampAwningGeometry.point(u, 0, underside));
                assertInside(TraderCampAwningGeometry.SUPPORTS.get(u == 0 ? 2 : 3),
                        TraderCampAwningGeometry.point(u, 1, underside));
                assertInside(TraderCampAwningGeometry.SUPPORTS.get(4),
                        TraderCampAwningGeometry.point(u, 1, underside));
            }
        }
    }

    @Test
    void cullBackedSheetIsClosedWithOppositeGeometryNormalsAndMatchingDiagonals() throws Exception {
        var faces = TraderCampAwningGeometry.CLOTH;
        assertEquals(82, faces.size());
        assertClosed(faces);
        for (int row = 0; row < TraderCampAwningGeometry.ROWS; row++) {
            for (int column = 0; column < TraderCampAwningGeometry.COLUMNS; column++) {
                int offset = row * (TraderCampAwningGeometry.COLUMNS * 2 + 2) + column * 2;
                var top = faces.get(offset);
                var bottom = faces.get(offset + 1);
                assertTrue(top.normal().y() > 0.9F);
                assertTrue(bottom.normal().y() < -0.9F);
                assertEquals(-1F, dot(top.normal(), bottom.normal()), 0.00001F);
                assertSeparated(top.a(), bottom.a());
                assertSeparated(top.b(), bottom.d());
                assertSeparated(top.c(), bottom.c());
                assertSeparated(top.d(), bottom.b());
                // Both Minecraft quad triangulations use a-to-c, avoiding warped-face intersections.
                assertEquals(top.a().u(), bottom.a().u());
                assertEquals(top.c().v(), bottom.c().v());
            }
        }
        for (var face : faces) assertWinding(face);
        String renderer = Files.readString(Path.of(
                "src/main/java/com/bettercontent/spiritcommerce/trader/TraderCampPostRenderer.java"));
        assertFalse(renderer.contains("entityCutoutNoCull"));
        assertTrue(renderer.contains("RenderType.entityCutout(WOOL)"));
    }

    @Test
    void clothTextureFootprintIsUnchangedAndTimberUvUsesPhysicalFaceLengths() {
        for (var face : TraderCampAwningGeometry.CLOTH) {
            for (var vertex : face.vertices()) {
                assertTrue(vertex.u() >= 0 && vertex.u() <= 1);
                assertTrue(vertex.v() >= 0 && vertex.v() <= 1);
                assertEquals(-0.55F + 1.1F * vertex.u(), vertex.x(), 0.000001F);
                assertEquals(0.03F + 2.02F * vertex.v(), vertex.z(), 0.000001F);
            }
        }
        for (var box : TraderCampAwningGeometry.SUPPORTS) {
            var faces = TraderCampAwningGeometry.boxFaces(box);
            assertClosed(faces);
            for (var face : faces) {
                assertWinding(face);
                assertEquals(distance(face.a(), face.b()), face.b().u() - face.a().u(), 0.000001F);
                assertEquals(distance(face.a(), face.d()), face.a().v() - face.d().v(), 0.000001F);
                var n = face.normal();
                float cx = (face.a().x() + face.b().x() + face.c().x() + face.d().x()) / 4;
                float cy = (face.a().y() + face.b().y() + face.c().y() + face.d().y()) / 4;
                float cz = (face.a().z() + face.b().z() + face.c().z() + face.d().z()) / 4;
                assertTrue(n.x() * (cx - (box.x0()+box.x1())/2)
                        + n.y() * (cy - (box.y0()+box.y1())/2)
                        + n.z() * (cz - (box.z0()+box.z1())/2) > 0, "outward timber normal");
            }
        }
    }

    @Test
    void timberHasNoCoFacingCoplanarPositiveAreaOverlap() {
        var faces = TraderCampAwningGeometry.WOOD;
        float epsilon = 0.000001F;
        for (int i = 0; i < faces.size(); i++) {
            for (int j = i + 1; j < faces.size(); j++) {
                var a = faces.get(i);
                var b = faces.get(j);
                if (dot(a.normal(), b.normal()) < 0.99999F) continue;
                int axis = Math.abs(a.normal().x()) > 0.9F ? 0
                        : Math.abs(a.normal().y()) > 0.9F ? 1 : 2;
                if (Math.abs(coordinate(a.a(), axis) - coordinate(b.a(), axis)) > epsilon) continue;
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                assertFalse(overlap(a, b, u) > epsilon && overlap(a, b, v) > epsilon,
                        "Co-facing coplanar timber faces " + i + " and " + j);
            }
        }
    }

    private static double overlap(TraderCampAwningGeometry.Quad a, TraderCampAwningGeometry.Quad b, int axis) {
        var aa = a.vertices().stream().mapToDouble(p -> coordinate(p, axis)).summaryStatistics();
        var bb = b.vertices().stream().mapToDouble(p -> coordinate(p, axis)).summaryStatistics();
        return Math.min(aa.getMax(), bb.getMax()) - Math.max(aa.getMin(), bb.getMin());
    }

    private static float coordinate(TraderCampAwningGeometry.Vertex p, int axis) {
        return axis == 0 ? p.x() : axis == 1 ? p.y() : p.z();
    }

    @Test
    void actualSignedNativeFacingTransformsFollowAiStandAndFitExistingRenderBounds() {
        BlockPos pos = new BlockPos(31, 75, -47);
        AABB bounds = TraderCampPostBlockEntity.renderBounds(pos);
        for (Direction facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            Matrix4f rotation = new Matrix4f().rotateY((float) Math.toRadians(
                    TraderCampAwningGeometry.yawFor(facing.getStepX(), facing.getStepZ())));
            Vector3f approach = rotation.transformPosition(new Vector3f(0, 0, 1));
            assertEquals(facing.getStepX(), approach.x(), 0.000001F);
            assertEquals(facing.getStepZ(), approach.z(), 0.000001F);
            for (var face : java.util.stream.Stream.concat(TraderCampAwningGeometry.CLOTH.stream(),
                    TraderCampAwningGeometry.WOOD.stream()).toList()) {
                for (var p : face.vertices()) {
                    Vector3f world = rotation.transformPosition(new Vector3f(p.x(), p.y(), p.z()));
                    assertTrue(bounds.contains(pos.getX()+0.5+world.x(), pos.getY()+world.y(),
                            pos.getZ()+0.5+world.z()), "bounds for " + facing + ": " + p);
                }
            }
            Vector3f stand = rotation.transformPosition(new Vector3f(0, 0, 1));
            assertTrue(TraderCampAwningGeometry.point(0.5F,
                    (stand.length()-TraderCampAwningGeometry.BACK_Z)/TraderCampAwningGeometry.LENGTH, true).y() >= 2.1F);
        }
    }

    private static void assertInside(TraderCampAwningGeometry.Box b, TraderCampAwningGeometry.Vertex p) {
        float e = 0.000001F;
        assertTrue(p.x() >= b.x0()-e && p.x() <= b.x1()+e);
        assertTrue(p.y() >= b.y0()-e && p.y() <= b.y1()+e);
        assertTrue(p.z() >= b.z0()-e && p.z() <= b.z1()+e);
    }

    private static void assertSeparated(TraderCampAwningGeometry.Vertex top, TraderCampAwningGeometry.Vertex bottom) {
        assertEquals(top.x(), bottom.x());
        assertEquals(top.z(), bottom.z());
        assertEquals(TraderCampAwningGeometry.THICKNESS, top.y()-bottom.y(), 0.000001F);
        assertEquals(top.u(), bottom.u());
        assertEquals(top.v(), bottom.v());
    }

    private static float dot(TraderCampAwningGeometry.Normal a, TraderCampAwningGeometry.Normal b) {
        return a.x()*b.x() + a.y()*b.y() + a.z()*b.z();
    }

    private static float distance(TraderCampAwningGeometry.Vertex a, TraderCampAwningGeometry.Vertex b) {
        return new Vector3f(b.x()-a.x(), b.y()-a.y(), b.z()-a.z()).length();
    }

    private static void assertWinding(TraderCampAwningGeometry.Quad face) {
        var n = face.normal();
        assertEquals(1F, Math.sqrt(n.x()*n.x()+n.y()*n.y()+n.z()*n.z()), 0.000001F);
        var a = face.a();
        Vector3f b = new Vector3f(face.b().x()-a.x(), face.b().y()-a.y(), face.b().z()-a.z());
        Vector3f c = new Vector3f(face.c().x()-a.x(), face.c().y()-a.y(), face.c().z()-a.z());
        Vector3f d = new Vector3f(face.d().x()-a.x(), face.d().y()-a.y(), face.d().z()-a.z());
        assertTrue(b.cross(c, new Vector3f()).dot(n.x(), n.y(), n.z()) > 0);
        assertTrue(c.cross(d, new Vector3f()).dot(n.x(), n.y(), n.z()) > 0);
    }

    private record Position(float x, float y, float z) {
        static Position of(TraderCampAwningGeometry.Vertex v) { return new Position(v.x(), v.y(), v.z()); }
    }
    private record Edge(Position a, Position b) { }

    private static void assertClosed(List<TraderCampAwningGeometry.Quad> faces) {
        Map<Edge, Integer> edges = new HashMap<>();
        for (var face : faces) {
            var v = face.vertices();
            for (int i = 0; i < 4; i++) {
                Edge edge = new Edge(Position.of(v.get(i)), Position.of(v.get((i+1)%4)));
                assertFalse(edges.containsKey(edge), "duplicate same-winding face edge");
                edges.put(edge, 1);
            }
        }
        for (Edge edge : edges.keySet()) {
            assertTrue(edges.containsKey(new Edge(edge.b(), edge.a())), "open mesh edge " + edge);
        }
    }
}
