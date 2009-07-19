package org.jbox2d.collision.shapes;

import org.jbox2d.collision.AABB;
import org.jbox2d.collision.MassData;
import org.jbox2d.collision.Segment;
import org.jbox2d.collision.SegmentCollide;
import org.jbox2d.collision.SupportsGenericDistance;
import org.jbox2d.common.RaycastResult;
import org.jbox2d.common.Settings;
import org.jbox2d.common.Vec2;
import org.jbox2d.common.XForm;
import org.jbox2d.dynamics.Body;

/**
 * An edge shape.  Create using {@link Body#createShape(ShapeDef)} with an {@link EdgeChainDef},
 * not the constructor here.
 * @see Body#createShape(ShapeDef)
 * @see EdgeChainDef
 * @author daniel
 */
public class EdgeShape extends Shape implements SupportsGenericDistance {
	//private updatesweepradius
	private final Vec2 m_v1;
	private final Vec2 m_v2;
	private final Vec2 m_coreV1;
	private final Vec2 m_coreV2;
	private final float m_length;
	private final Vec2 m_normal;
	private final Vec2 m_direction;
	// Unit vector halfway between m_direction and m_prevEdge.m_direction:
	private final Vec2 m_cornerDir1;
	// Unit vector halfway between m_direction and m_nextEdge.m_direction:
	private final Vec2 m_cornerDir2;
	private boolean m_cornerConvex1;
	private boolean m_cornerConvex2;
	EdgeShape m_nextEdge;
	EdgeShape m_prevEdge;

	/**
	 * Don't use this.  Instead create using {@link Body#createShape(ShapeDef)} with an
	 * {@link EdgeChainDef}, not the constructor here.
	 * @see Body#createShape(ShapeDef)
	 * @see EdgeChainDef
	 * @param v1
	 * @param v2
	 * @param def
	 */
	public EdgeShape(final Vec2 v1, final Vec2 v2, final ShapeDef def) {
		super(def);
		assert(def.type == ShapeType.EDGE_SHAPE);

		m_type = ShapeType.EDGE_SHAPE;

		m_prevEdge = null;
		m_nextEdge = null;

		m_v1 = v1;
		m_v2 = v2;

		m_direction = m_v2.sub(m_v1);
		m_length = m_direction.normalize();
		m_normal = new Vec2(m_direction.y, -m_direction.x);

		// djm they are new objects after that first math call
		m_coreV1 = (m_normal.sub(m_direction)).mulLocal(-Settings.toiSlop).addLocal(m_v1);
		m_coreV2 = (m_normal.add(m_direction)).mulLocal(-Settings.toiSlop).addLocal(m_v2);

		m_cornerDir1 = m_normal.clone();
		m_cornerDir2 = m_normal.mul(-1.0f);
	}

	/**
	 * @see Shape#updateSweepRadius(Vec2)
	 */
	@Override
	public void updateSweepRadius(final Vec2 center) {
		// Update the sweep radius (maximum radius) as measured from
		// a local center point.
		final float dx = m_coreV1.x - center.x;
		final float dy = m_coreV1.y - center.y;
		final float d1 = dx*dx+dy*dy;
		final float dx2 = m_coreV2.x - center.x;
		final float dy2 = m_coreV2.y - center.y;
		final float d2 = dx2*dx2+dy2*dy2;
		m_sweepRadius = (float)Math.sqrt(d1 > d2 ? d1 : d2);
	}

	/**
	 * @see Shape#testPoint(XForm, Vec2)
	 */
	@Override
	public boolean testPoint(final XForm transform, final Vec2 p) {
		// djm this could use some optimization.
		return false;
	}

	// djm pooled
	private final Vec2 r = new Vec2();
	private final Vec2 v1 = new Vec2();
	private final Vec2 d = new Vec2();
	private final Vec2 n = new Vec2();
	private final Vec2 b = new Vec2();
	/**
	 * @see Shape#testSegment(XForm, RaycastResult, Segment, float)
	 */
	@Override
	public SegmentCollide testSegment(final XForm xf, final RaycastResult out, final Segment segment, final float maxLambda){
		r.set(segment.p2).subLocal(segment.p1);
		XForm.mulToOut( xf, m_v1, v1);
		XForm.mulToOut( xf, m_v2, d);
		d.subLocal(v1);
		Vec2.crossToOut(d, 1.0f, n);

		final float k_slop = 100.0f * Settings.EPSILON;
		final float denom = -Vec2.dot(r, n);

		// Cull back facing collision and ignore parallel segments.
		if (denom > k_slop)
		{
			// Does the segment intersect the infinite line associated with this segment?
			b.set(segment.p1).subLocal(v1);
			float a = Vec2.dot(b, n);

			if (0.0f <= a && a <= maxLambda * denom)
			{
				final float mu2 = -r.x * b.y + r.y * b.x;

				// Does the segment intersect this segment?
				if (-k_slop * denom <= mu2 && mu2 <= denom * (1.0f + k_slop))
				{
					a /= denom;
					n.normalize();
					out.lambda = a;
					out.normal.set(n);
					return SegmentCollide.HIT_COLLIDE;
				}
			}
		}

		return SegmentCollide.MISS_COLLIDE;
	}

	/**
	 * @see Shape#computeAABB(AABB, XForm)
	 */
	@Override
	public void computeAABB(final AABB aabb, final XForm transform) {
		/*Vec2 v1 = XForm.mul(transform, m_v1);
		Vec2 v2 = XForm.mul(transform, m_v2);
		aabb.lowerBound = Vec2.min(v1, v2);
		aabb.upperBound = Vec2.max(v1, v2);*/

		// djm we avoid one creation. crafty huh?  i won't bother
		// pooling one creation, as this method isn't hot
		XForm.mulToOut(transform, m_v1, aabb.lowerBound);
		final Vec2 v2 = XForm.mul(transform, m_v2);

		Vec2.maxToOut(aabb.lowerBound, v2, aabb.upperBound);
		Vec2.minToOut(aabb.lowerBound, v2, aabb.lowerBound);
	}

	// djm pooling

	private final Vec2 sweptV1 = new Vec2();
	private final Vec2 sweptV2 = new Vec2();
	private final Vec2 sweptV3 = new Vec2();
	private final Vec2 sweptV4 = new Vec2();

	/**
	 * @see Shape#computeSweptAABB(AABB, XForm, XForm)
	 */
	@Override
	public void computeSweptAABB(final AABB aabb, final XForm transform1, final XForm transform2) {
		// djm this method is pretty hot (called every time step)

		XForm.mulToOut(transform1, m_v1, sweptV1);
		XForm.mulToOut(transform1, m_v2, sweptV2);
		XForm.mulToOut(transform2, m_v1, sweptV3);
		XForm.mulToOut(transform2, m_v2, sweptV4);

		//aabb.lowerBound = Vec2.min(Vec2.min(Vec2.min(v1, v2), v3), v4);
		//aabb.upperBound = Vec2.max(Vec2.max(Vec2.max(v1, v2), v3), v4);

		// djm ok here's the non object-creation-crazy way
		Vec2.minToOut( sweptV1, sweptV2, aabb.lowerBound);
		Vec2.minToOut( aabb.lowerBound, sweptV3, aabb.lowerBound);
		Vec2.minToOut( aabb.lowerBound, sweptV4, aabb.lowerBound);

		Vec2.maxToOut( sweptV1, sweptV2, aabb.upperBound);
		Vec2.maxToOut( aabb.upperBound, sweptV3, aabb.upperBound);
		Vec2.maxToOut( aabb.upperBound, sweptV4, aabb.upperBound);
	}

	/**
	 * @see Shape#computeMass(MassData)
	 */
	@Override
	public void computeMass(final MassData massData) {
		massData.mass = 0;
		massData.center.set(m_v1);

		// inertia about the local origin
		massData.I = 0;
	}

	// djm pooled
	private final Vec2 supportV1 = new Vec2();
	private final Vec2 supportV2 = new Vec2();
	/**
	 * @see SupportsGenericDistance#support(Vec2, XForm, Vec2)
	 */
	public void support(final Vec2 dest, final XForm xf, final Vec2 d) {
		XForm.mulToOut(xf, m_coreV1, supportV1);
		XForm.mulToOut(xf, m_coreV2, supportV2);
		dest.set(Vec2.dot(supportV1, d) > Vec2.dot(supportV2, d) ? supportV1 : supportV2);
	}

	public void setPrevEdge(final EdgeShape edge, final Vec2 core, final Vec2 cornerDir, final boolean convex) {
		m_prevEdge = edge;
		m_coreV1.set(core);
		m_cornerDir1.set(cornerDir);
		m_cornerConvex1 = convex;
	}

	public void setNextEdge(final EdgeShape edge, final Vec2 core, final Vec2 cornerDir, final boolean convex) {
		m_nextEdge = edge;
		m_coreV2.set(core);
		m_cornerDir2.set(cornerDir);
		m_cornerConvex2 = convex;
	}

	/** Linear distance from vertex1 to vertex2 */
	public float getLength() {
		return m_length;
	}

	/** Local position of vertex in parent body */
	public Vec2 getVertex1() {
		return m_v1;
	}

	/** Local position of vertex in parent body */
	public Vec2 getVertex2() {
		return m_v2;
	}

	/** "Core" vertex with TOI slop for b2Distance functions */
	public Vec2 getCoreVertex1() {
		return m_coreV1;
	}

	/** "Core" vertex with TOI slop for b2Distance functions */
	public Vec2 getCoreVertex2() {
		return m_coreV2;
	}

	/** Perpendecular unit vector point, pointing from the solid side to the empty side. */
	public Vec2 getNormalVector() {
		return m_normal;
	}

	/** Parallel unit vector, pointing from vertex1 to vertex2 */
	public Vec2 getDirectionVector() {
		return m_direction;
	}

	public Vec2 getCorner1Vector() {
		return m_cornerDir1;
	}

	public Vec2 getCorner2Vector() {
		return m_cornerDir2;
	}

	/** Get the next edge in the chain. */
	public EdgeShape getNextEdge() {
		return m_nextEdge;
	}

	/** Get the previous edge in the chain. */
	public EdgeShape getPrevEdge() {
		return m_prevEdge;
	}

	/**
	 * @see SupportsGenericDistance#getFirstVertexToOut(XForm, Vec2)
	 */
	public void getFirstVertexToOut(final XForm xf, final Vec2 out) {
		XForm.mulToOut(xf, m_coreV1, out);
	}

	public boolean corner1IsConvex() {
		return m_cornerConvex1;
	}

	public boolean corner2IsConvex() {
		return m_cornerConvex2;
	}
	
	public float computeSubmergedArea(final Vec2 normal,float offset,XForm xf,Vec2 c) {
		//Note that v0 is independent of any details of the specific edge
		//We are relying on v0 being consistent between multiple edges of the same body
		Vec2 v0 = normal.mul(offset);
		//b2Vec2 v0 = xf.position + (offset - b2Dot(normal, xf.position)) * normal;

		Vec2 v1 = XForm.mul(xf, m_v1);
		Vec2 v2 = XForm.mul(xf, m_v2);

		float d1 = Vec2.dot(normal, v1) - offset;
		float d2 = Vec2.dot(normal, v2) - offset;

		if (d1 > 0.0f)
		{
			if (d2 > 0.0f)
			{
				return 0.0f;
			}
			else
			{
				v1 = v1.mul(-d2 / (d1 - d2)).add(v2.mul(d1 / (d1 - d2)));
			}
		}
		else
		{
			if (d2 > 0.0f)
			{
				v2 = v1.mul(-d2 / (d1 - d2)).add(v2.mul(d1 / (d1 - d2)));
			}
			else
			{
				//Nothing
			}
		}

		// v0,v1,v2 represents a fully submerged triangle
		float k_inv3 = 1.0f / 3.0f;

		// Area weighted centroid
		c.x = k_inv3 * (v0.x + v1.x + v2.x);
		c.y = k_inv3 * (v0.y + v1.y + v2.y);

		Vec2 e1 = v1.sub(v0);
		Vec2 e2 = v2.sub(v0);

		return 0.5f * Vec2.cross(e1, e2);
	}

}
