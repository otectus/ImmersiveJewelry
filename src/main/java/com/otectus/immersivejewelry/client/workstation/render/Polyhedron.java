package com.otectus.immersivejewelry.client.workstation.render;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A convex polyhedron as a list of convex faces, cut down by half-spaces. This is how a stone takes its shape: the
 * rough stone is a convex blob and every worked region is a plane moving into it as material is removed, so the faces
 * that appear are exactly where the lap met the stone. Bounded: a stone has at most a few dozen planes.
 */
public final class Polyhedron {
    /** Tags for faces that belong to no working region. */
    public static final int ROUGH = -1, TABLE = -2, PAVILION = -3, GIRDLE = -4, CHIP = -5;

    public record Face(List<Vector3f> points, Vector3f normal, int tag) {}

    private final List<Face> faces;

    private Polyhedron(List<Face> faces) {
        this.faces = faces;
    }

    public List<Face> faces() {
        return faces;
    }

    /** An axis-aligned box with half-sizes {@code hx}, {@code hy}, {@code hz}, all faces tagged {@code tag}. */
    public static Polyhedron box(float hx, float hy, float hz, int tag) {
        Vector3f[] c = {
                new Vector3f(-hx, -hy, -hz), new Vector3f(hx, -hy, -hz), new Vector3f(hx, hy, -hz), new Vector3f(-hx, hy, -hz),
                new Vector3f(-hx, -hy, hz), new Vector3f(hx, -hy, hz), new Vector3f(hx, hy, hz), new Vector3f(-hx, hy, hz)};
        List<Face> faces = new ArrayList<>();
        faces.add(face(new Vector3f(0, 0, -1), tag, c[1], c[0], c[3], c[2]));
        faces.add(face(new Vector3f(0, 0, 1), tag, c[4], c[5], c[6], c[7]));
        faces.add(face(new Vector3f(-1, 0, 0), tag, c[0], c[4], c[7], c[3]));
        faces.add(face(new Vector3f(1, 0, 0), tag, c[5], c[1], c[2], c[6]));
        faces.add(face(new Vector3f(0, 1, 0), tag, c[3], c[7], c[6], c[2]));
        faces.add(face(new Vector3f(0, -1, 0), tag, c[4], c[0], c[1], c[5]));
        return new Polyhedron(faces);
    }

    private static Face face(Vector3f normal, int tag, Vector3f... points) {
        List<Vector3f> list = new ArrayList<>();
        for (Vector3f p : points) list.add(new Vector3f(p));
        return new Face(list, normal, tag);
    }

    /** How far the shape reaches along {@code direction} (unit). */
    public float support(Vector3f direction) {
        float max = -Float.MAX_VALUE;
        for (Face f : faces) for (Vector3f p : f.points()) max = Math.max(max, p.dot(direction));
        return max;
    }

    /** Distance to the surface along a ray from the centre (for a dop bonded to the back of the stone). */
    public float surfaceRadius(Vector3f direction) {
        float radius = Float.POSITIVE_INFINITY;
        for (Face face : faces) {
            float along = face.normal().dot(direction);
            if (along > 1.0E-6F && !face.points().isEmpty())
                radius = Math.min(radius, face.normal().dot(face.points().get(0)) / along);
        }
        return Float.isFinite(radius) ? Math.max(0F, radius) : 0F;
    }

    /**
     * Keeps the part with {@code p . normal <= offset}; the new face this opens is tagged {@code tag}. A plane that misses
     * the shape leaves it as it is.
     */
    public Polyhedron clip(Vector3f normal, float offset, int tag) {
        Vector3f n = new Vector3f(normal).normalize();
        List<Face> out = new ArrayList<>();
        List<Vector3f> cut = new ArrayList<>();
        boolean changed = false;
        for (Face f : faces) {
            List<Vector3f> pts = f.points();
            List<Vector3f> kept = new ArrayList<>();
            int count = pts.size();
            for (int i = 0; i < count; i++) {
                Vector3f a = pts.get(i);
                Vector3f b = pts.get((i + 1) % count);
                float da = a.dot(n) - offset;
                float db = b.dot(n) - offset;
                // Keep intersections already on the plane in its cap as well. A plane through an existing
                // vertex/edge otherwise opens a hole; subsequent cuts then lose entire portions of the stone.
                if (Math.abs(da) < 1.0E-6F) {
                    a = new Vector3f(a).sub(new Vector3f(n).mul(da));
                    da = 0F;
                }
                if (Math.abs(db) < 1.0E-6F) db = 0F;
                if (da <= 0F) kept.add(a);
                else changed = true;
                if (da == 0F) cut.add(new Vector3f(a));
                if ((da < 0F && db > 0F) || (da > 0F && db < 0F)) {
                    float t = da / (da - db);
                    Vector3f p = new Vector3f(a).lerp(b, t);
                    kept.add(p);
                    cut.add(p);
                }
            }
            if (kept.size() >= 3) out.add(new Face(kept, f.normal(), f.tag()));
        }
        if (!changed) return this;
        if (cut.size() >= 3) {
            List<Vector3f> polygon = cap(cut, n);
            if (polygon.size() >= 3 && hasArea(polygon)) out.add(new Face(polygon, new Vector3f(n), tag));
        }
        return new Polyhedron(out);
    }

    private static boolean hasArea(List<Vector3f> points) {
        Vector3f origin = points.get(0);
        for (int i = 1; i + 1 < points.size(); i++) {
            if (new Vector3f(points.get(i)).sub(origin).cross(new Vector3f(points.get(i + 1)).sub(origin))
                    .lengthSquared() > 1.0E-12F) return true;
        }
        return false;
    }

    /** The cap polygon of a cut: the intersection points sorted round their centre, counter-clockwise seen along {@code n}. */
    private static List<Vector3f> cap(List<Vector3f> points, Vector3f n) {
        Vector3f centre = new Vector3f();
        for (Vector3f p : points) centre.add(p);
        centre.div(points.size());
        Vector3f u = Math.abs(n.y) < 0.9F ? new Vector3f(0, 1, 0).cross(n).normalize() : new Vector3f(1, 0, 0).cross(n).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        List<Vector3f> unique = new ArrayList<>();
        outer:
        for (Vector3f p : points) {
            for (Vector3f q : unique) if (q.distanceSquared(p) < 1.0E-12F) continue outer;
            unique.add(p);
        }
        unique.sort((a, b) -> {
            Vector3f da = new Vector3f(a).sub(centre);
            Vector3f db = new Vector3f(b).sub(centre);
            double aa = Math.atan2(da.dot(v), da.dot(u));
            double ab = Math.atan2(db.dot(v), db.dot(u));
            return Double.compare(aa, ab);
        });
        return unique;
    }

    /** Uniformly scaled copy (non-uniform scales keep normals right for the axis-aligned stretch used by step cuts). */
    public Polyhedron scaled(float sx, float sy, float sz) {
        List<Face> out = new ArrayList<>(faces.size());
        for (Face f : faces) {
            List<Vector3f> pts = new ArrayList<>(f.points().size());
            for (Vector3f p : f.points()) pts.add(new Vector3f(p.x * sx, p.y * sy, p.z * sz));
            Vector3f n = new Vector3f(f.normal().x / sx, f.normal().y / sy, f.normal().z / sz).normalize();
            out.add(new Face(pts, n, f.tag()));
        }
        return new Polyhedron(out);
    }
}
