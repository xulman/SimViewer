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

/** Corresponds to one element that simulator's DrawVector() can send.
    Compared to the superclass Vector, this one recognizes vector as
    two graphical elements, a Shaft and a Head, and has therefore more
    housekeeping attributes.
    The class governs all necessary pieces of information to display
    (user-defined scaled version of) the vector, and the SciView's Nodes are
    pointed inside this class to (re)fetch the actual display data/instructions. */
public class VectorSH extends Vector
{
	public VectorSH() //without connection to SciView
	{
		super();
		nodeHead = null;
	}

	public VectorSH(final Node shaftNode, //with connection to SciView
	                final Node headNode)
	{
		super(shaftNode);
		nodeHead = headNode;
	}

	// ------- main defining attributes -------
	/** reference on the graphics Node that draws the vector's head,
	    reference on the Node that draws vector's shaft is in super.node */
	public final Node nodeHead;

	// ------- derived (aux) attributes -------
	/** similar to the idea behind super.auxScale, we host this extra
	    memory to adjust separately the scaling of the vector's head */
	public final Vector3f auxScaleHead = new Vector3f(1);

	/** this attribute is a function of vector:
	    it defines the position/placement of the head of the vector */
	public final Vector3f auxHeadBase = new Vector3f(0);

	// ------- setters -------
	/** shadow/override the superclass'es applyScale() with a new one
	    that can provide default values for the 'headPosRatio' */
	@Override
	public void applyScale(final float scale)
	{
		this.applyScale(scale,0.2f);
	}

	/** shadow/override the superclass'es update() with a new one
	    that can provide default values for the 'headPosRatio' */
	@Override
	public void update(final Vector v)
	{
		this.update(v,0.2f);
	}


	/** adjusts aux attribs to draw the vector scale-times
	    larger than what it is originally; if such scaling
	    is required, it must not be called before this.update() */
	public void applyScale(final float scale, final float headPosRatio)
	{
		super.applyScale(scale);

		//how to scale the head?
		// - longitudially/axially the same as the vector's shaft
		auxScaleHead.y = auxScale.y;
		//
		// - laterally don't scale (that is keep fixed absolute diameter)
		//   unless the head's length will be much shorter than the head's width
		//   in which case we start down-scaling proportionally
		auxScaleHead.x = Math.min(auxScaleHead.y, 1f);
		auxScaleHead.z = auxScaleHead.x;

		auxHeadBase.set( vector ).mulAdd(scale * (1f-headPosRatio), base);
	}

	/** clones the given 'v' into this vector, and updates all necessary aux attribs */
	public void update(final Vector v, final float headPosRatio)
	{
		updateAndScale(v,1f,headPosRatio);
	}


	/** short cut method (also sligtly more performance-optimal)
	    to replace constructs: v.update(V); v.applyScale(scale,headPosRatio); */
	public void updateAndScale(final Vector v,
	                           final float scale, final float headPosRatio)
	{
		super.update(v);
		this.applyScale(scale,headPosRatio);
	}
}
