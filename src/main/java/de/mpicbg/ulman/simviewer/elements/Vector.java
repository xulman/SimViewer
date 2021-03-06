/*
BSD 2-Clause License

Copyright (c) 2019, Vladimír Ulman
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/


package de.mpicbg.ulman.simviewer.elements;

import graphics.scenery.Node;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Corresponds to one element that simulator's DrawVector() can send.
    The class governs all necessary pieces of information to display
    (user-defined scaled version of) the vector, and the SciView's Nodes are
    pointed inside this class to (re)fetch the actual display data/instructions. */
public class Vector
{
	public Vector()             { node = null; }   //without connection to SciView
	public Vector(final Node v) { node = v; }      //  with  connection to SciView

	// ------- main defining attributes -------
	public final Node node;
	public final Vector3f base   = new Vector3f(0); //NB: zeros all three elems
	public final Vector3f vector = new Vector3f(0);

	/** object's color in the RGB format */
	public final Vector3f colorRGB = new Vector3f(0.2f,1.0f,0.2f);

	/** object's "slave/dependent" color in the RGBA format for the full instancing */
	private final Vector4f colorRGBA = new Vector4f(1.0f);
	public Vector4f getColorRGBA()
	{
		colorRGBA.x = colorRGB.x;
		colorRGBA.y = colorRGB.y;
		colorRGBA.z = colorRGB.z;
		return colorRGBA;
	}

	public int lastSeenTick = 0;

	// ------- derived (aux) attributes -------
	/** this attribute is a function of vector:
	    auxScale.y is the vector length (because master instance vector
	    is oriented along y axis, so we elongate it to the desired length
	    only along this axis (and then rotate, then place to 'base')),
	    auxScale.x and .z should remain 1 (= no scaling) */
	public final Vector3f auxScale = new Vector3f(1);

	// ------- setters -------
	/** adjusts aux attribs to draw the vector scale-times
	    larger than what it is originally; if such scaling
	    is required, it must be called right after this.update() */
	public void applyScale(final float scale)
	{
		auxScale.y = scale * vector.length();
	}

	/** clones the given 'v' into this vector, and updates all necessary aux attribs */
	public void update(final Vector v)
	{
		updateAndScale(v,1f);
	}

	/** clones the given 'v' into this vector, and updates all necessary aux attribs;
	    it is a short cut method (also sligtly more performance-optimal)
	    to replace constructs: v.update(V); v.applyScale(scale); */
	public void updateAndScale(final Vector v, final float scale)
	{
		base.set( v.base );
		vector.set( v.vector );
		colorRGB.set( v.colorRGB );

		applyScale(scale);
	}
}
