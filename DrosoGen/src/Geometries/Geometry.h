#ifndef GEOMETRY_H
#define GEOMETRY_H

#include <list>
#include "../util/Vector3d.h"

/** accuracy of the geometry representation, choose float or double */
#define FLOAT float

/** An x,y,z-axes aligned 3D bounding box for approximate representation of agent's geometry */
class AxisAlignedBoundingBox
{
public:
	/** defines the bounding box with its volumetric diagonal,
	    this is the "bottom-left" corner of the box [micrometers] */
	Vector3d<FLOAT> minCorner;

	/** defines the bounding box with its volumetric diagonal,
	    this is the "upper-right" corner of the box [micrometers] */
	Vector3d<FLOAT> maxCorner;

	/** construct an empty AABB */
	AxisAlignedBoundingBox(void)
	{
		reset();
	}

	/** resets the BB (make it ready for someone to start filling it) */
	void inline reset(void)
	{
		minCorner = FLOAT(+999999999.0);
		maxCorner = FLOAT(-999999999.0);
	}
};


/**
 * Representation of a nearby pair of points, an output of Geometry::getDistance().
 * The geometry object on which getDistance() is called plays the role of 'local' and
 * the argument of the method plays the role of 'other' in the nomenclature of attribute
 * names. If the distance between the two points is negative, the points represent
 * a colliding pair.
 *
 * The structure can possibly hold 'helper data' that depend on form of the geometry that
 * is investigated during the getDistance(). This can be, e.g., a pointer on mesh vertex
 * that is in collision, or centre of a sphere that is in collision.
 */
struct ProximityPair
{
	/** position of 'local' colliding point [micrometers] */
	Vector3d<FLOAT> localPos;
	/** position of 'other' colliding point [micrometers] */
	Vector3d<FLOAT> otherPos;

	/** Distance between the localPos and otherPos [micrometers].
	    If the value is negative, the two points represent a collision pair.
	    If the value is positive, the two points are assumed to form a pair
	    of two nearest points between the two geometries. */
	FLOAT distance;

	/** pointer on hinting data of the 'local' point */
	void* localHint;
	/** pointer on hinting data of the 'other' point */
	void* otherHint;

	/** convenience constructor for just two colliding points */
	ProximityPair(const Vector3d<FLOAT>& l, const Vector3d<FLOAT>& o,
	              const FLOAT dist)
		: localPos(l), otherPos(o), distance(dist), localHint(NULL), otherHint(NULL) {};

	/** convenience constructor for points with hints */
	ProximityPair(const Vector3d<FLOAT>& l, const Vector3d<FLOAT>& o,
	              const FLOAT dist,
	              void* lh, void* oh)
		: localPos(l), otherPos(o), distance(dist), localHint(lh), otherHint(oh) {};

	/** swap the notion of 'local' and 'other' */
	void swap(void)
	{
		Vector3d<FLOAT> tmpV(localPos);
		localPos = otherPos;
		otherPos = tmpV;

		void* tmpH = localHint;
		localHint = otherHint;
		otherHint = tmpH;
	}
};

/** A constant to represent no collision situation */
static std::list<ProximityPair>* const emptyCollisionListPtr = new std::list<ProximityPair>();


/** A common ancestor class/type for the Spheres, Mesh and Mask Image
    representations of agent's geometry. It defines (pure virtual) methods
    to report collision pairs, see Geometry::getDistance(), between agents. */
class Geometry
{
protected:
	/** A variant of how the shape of an simulated agent is represented */
	typedef enum
	{
		Spheres=0,
		Mesh=1,
		MaskImg=2
	} ListOfShapeForms;

	/** Construct empty geometry object of given shape form.
	    This class should never be used constructed directly, always use some
	    derived class such as Spheres, Mesh or MaskImg. */
	Geometry(const ListOfShapeForms _shapeForm) : shapeForm(_shapeForm), AABB() {};


public:
	/** choosen form of shape representation */
	const ListOfShapeForms shapeForm;

	/** optional, i.e. not automatically updated with every geometry change,
	    bounding box to outline where the agent lives in the scene */
	AxisAlignedBoundingBox AABB;

	/** Calculate and determine collision pairs, if any, between
	    myself and some other agent. A (scaled) ForceVector<FLOAT> can
	    be easily constructed from the points of the ProximityPair.
	    If no collision is found, emptyCollisionListPtr is returned. */
	virtual
	std::list<ProximityPair>*
	getDistance(const Geometry& otherGeometry) const =0;

	/** sets the given AABB to reflect the current geometry */
	virtual
	void setAABB(AxisAlignedBoundingBox& AABB) const =0;

	/** sets this object's own AABB to reflect the current geometry */
	void setAABB(void)
	{
		setAABB(this->AABB);
	}
};
#endif
