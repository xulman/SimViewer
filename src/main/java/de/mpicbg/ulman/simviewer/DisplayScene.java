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


package de.mpicbg.ulman.simviewer;

import org.joml.Vector3f;
import graphics.scenery.*;
import org.scijava.ui.behaviour.ClickBehaviour;
import sc.iview.SciView;
import java.io.PrintStream;
import java.util.Map;
import java.util.HashMap;
import de.mpicbg.ulman.simviewer.elements.Point;
import de.mpicbg.ulman.simviewer.elements.Line;
import de.mpicbg.ulman.simviewer.elements.Vector;
import de.mpicbg.ulman.simviewer.elements.VectorSH;
import de.mpicbg.ulman.simviewer.util.Palette;
import de.mpicbg.ulman.simviewer.util.SceneAxesData;
import de.mpicbg.ulman.simviewer.util.SceneBorderData;

/**
 * Adapted from TexturedCubeJavaExample.java from the scenery project,
 * originally created by kharrington on 7/6/16.
 *
 * This file was created and is being developed by Vladimir Ulman, 2018.
 */
public class DisplayScene
{
	/** constructor to create an empty window */
	public
	DisplayScene(final SciView sciView,
	             final float[] sOffset, final float[] sSize)
	{
		if (sOffset.length != 3 || sSize.length != 3)
			throw new RuntimeException("Offset and Size must be 3-items long: 3D world!");

		//init the scene dimensions
		sceneOffset  = sOffset.clone();
		sceneSize    = sSize.clone();
		this.sciView = sciView;

		//the overall down scaling of the displayed objects such that moving around
		//the scene with SciView is vivid (move step size is fixed in SciView), at
		//the same time we want the objects and distances to be defined with our
		//non-scaled coordinates -- so we hook everything underneath the fake object
		//that is downscaled (and consequently all is downscaled too) but defined with
		//at original scale (with original coordinates and distances)
		DsFactor = 0.2f;

		//introduce an invisible "fake" object
		scene = new Box(new Vector3f(0));
		scene.setScale(new Vector3f(DsFactor));
		scene.setName("SimViewer");
		sciView.addNode(scene);

		sceneGlobalCoordCentre = new Box();
		sceneGlobalCoordCentre.setName("SimViewer's center");

		adaptSceneBBoxAndCentreNode();
		sciView.addNode(sceneGlobalCoordCentre);

		//init the colors -- the material lookup table
		final Material sampleMat = new Material();
		sampleMat.setCullingMode(Material.CullingMode.None);
		sampleMat.setAmbient(  new Vector3f(1.0f, 1.0f, 1.0f) );
		sampleMat.setSpecular( new Vector3f(1.0f, 1.0f, 1.0f) );

		materials = new Palette(1);
		materials.setMaterialsAlike(sampleMat);
	}

	/** attempts to clean up and close this rendering window */
	public
	void stop()
	{
		if (fixedLights != null) RemoveFixedLightsRamp();
		if (axesData != null) RemoveDisplayAxes();
		if (borderData != null) RemoveDisplaySceneBorder();

		//remove the rest of the SimViewer, which is now only the remaining user's data...
		sciView.deleteNode(sceneGlobalCoordCentre);
		sciView.deleteNode(scene);
	}

	public
	void refreshInspectorPanel()
	{
		//sciView.requestPropEditorRefresh(scene);
		sciView.mainWindow.rebuildSceneTree(); //TODO, a bit brutal solution for now....
	}

	public
	void setSceneName(final String newName)
	{
		scene.setName(newName);
	}

	protected
	Sphere factoryForPoints()
	{
		return new Sphere(1.0f, 12);
	}

	protected
	Cylinder factoryForLines()
	{
		return new Cylinder(0.3f, 1.0f, 4);
	}

	protected
	Cylinder factoryForVectorShafts()
	{
		return new Cylinder(0.3f, 1.0f-vec_headLengthRatio, 4);
	}

	protected
	Cone factoryForVectorHeads()
	{
		return new Cone(vec_headToShaftWidthRatio * 0.3f, vec_headLengthRatio, 4, new Vector3f(0,1,0));
	}

	final float vec_headLengthRatio = 0.2f;        //relative scale (0,1)
	final float vec_headToShaftWidthRatio = 3.0f;  //absolute value/width
	//----------------------------------------------------------------------------


	/** short cut to the sciView itself - the main root of the scene graph */
	final SciView sciView;

	/** short cut to the root Node underwhich all displayed objects should be hooked up,
	    this one is typically hooked right under the this.sciView */
	final Node scene;
	final Node sceneGlobalCoordCentre;

	/** 3D position of the scene, to position well the lights and camera */
	final float[] sceneOffset;
	/** 3D size (diagonal vector) of the scene, to position well the lights and camera */
	final float[] sceneSize;

	/** the common scaling factor applied on all spatial units before their submitted to the scene */
	float DsFactor;

	/** fixed lookup table with colors, in the form of materials... */
	Palette materials;
	//----------------------------------------------------------------------------

	void requestWorldUpdate(boolean force)
	{
		//intentionally empty
	}
	//----------------------------------------------------------------------------

	public
	boolean IsFrontFacesCullingEnabled()
	{ return (materials.getMaterial(0).getCullingMode() == Material.CullingMode.Front); }

	/** attempts to turn on/off the "push mode", and reports the state */
	public
	boolean TogglePushMode()
	{
		sciView.setPushMode( !sciView.getPushMode() );
		return sciView.getPushMode();
	}

	/** resets the this.DsFactor scaling of the whole scene, without changing any coordinate
	    of any displayed (even when set to not visible) object, only the this.scene scaling
	    is affected; also lights are affected because they are treated separately in SimViewer */
	public
	void RescaleScene(final float newDsFactor)
	{
		RepositionFixedLightsRamp(newDsFactor);
		scene.setScale(new Vector3f(newDsFactor));
		sceneGlobalCoordCentre.getPosition().mul(newDsFactor/DsFactor);
		sceneGlobalCoordCentre.setNeedsUpdate(true);
		DsFactor = newDsFactor;
		requestWorldUpdate(false);
	}

	/** resets the scene offset and size to its current content plus 10 % relative margin,
	    and rebuilds and repositions the display axes (orientation compass), scene border and lights */
	public
	void ResizeScene()
	{
		ResizeScene(0.1f, 0.1f, 0.1f);
	}

	/** resets the scene offset and size to its current content plus given relative margin,
	    and rebuilds and repositions the display axes (orientation compass), scene border and lights */
	public
	void ResizeScene(final float... relativeMargin)
	{
		if (relativeMargin.length != sceneSize.length)
			throw new RuntimeException("Scene marging is of incompatible dimension.");

		//none of the get...BoundingBox() was working for me, so we do it ourselves
		//final OrientedBoundingBox box = scene.getMaximumBoundingBox();
		//
		//scan over all registered elements (Points, Lines, Vectors...) and determine the AABB
		final Vector3f min = new Vector3f(+99999999999.f);
		final Vector3f max = new Vector3f(-99999999999.f);
		final Vector3f tmp = new Vector3f();
		for (Point p : pointNodes.values())
		{
			//NB: radius should be non-negative
			updateMin(min, tmp.set(p.centre).sub(p.radius));
			updateMax(max, tmp.set(p.centre).add(p.radius));
		}
		for (Line l : lineNodes.values())
		{
			updateMin(min, tmp.set(l.base));
			updateMin(min, tmp.set(l.base).add(l.vector));
			updateMax(max, tmp.set(l.base));
			updateMax(max, tmp.set(l.base).add(l.vector));
		}
		for (VectorSH v : vectorNodes.values())
		{
			updateMin(min, tmp.set(v.base));
			updateMin(min, tmp.set(v.base).add(v.vector));
			updateMax(max, tmp.set(v.base));
			updateMax(max, tmp.set(v.base).add(v.vector));
		}

		sceneOffset[0] = min.x;
		sceneOffset[1] = min.y;
		sceneOffset[2] = min.z;

		sceneSize[0] = max.x;
		sceneSize[1] = max.y;
		sceneSize[2] = max.z;

		System.out.println("detected span: "
		       +sceneOffset[0]+"-"+sceneSize[0]+"  x  "
		       +sceneOffset[1]+"-"+sceneSize[1]+"  x  "
		       +sceneOffset[2]+"-"+sceneSize[2]);

		for (int d = 0; d < 3; ++d)
		{
			sceneSize[d] -= sceneOffset[d];
			sceneOffset[d] -= relativeMargin[d] * sceneSize[d];
			sceneSize[d] *= 1.f + (2.f * relativeMargin[d]);
		}

		this.ResizeScene(sceneOffset, sceneSize);
	}

	private void updateMin(final Vector3f min, final Vector3f pos)
	{
		min.x = Math.min( min.x,pos.x );
		min.y = Math.min( min.y,pos.y );
		min.z = Math.min( min.z,pos.z );
	}
	private void updateMax(final Vector3f max, final Vector3f pos)
	{
		max.x = Math.max( max.x,pos.x );
		max.y = Math.max( max.y,pos.y );
		max.z = Math.max( max.z,pos.z );
	}

	/** resets the scene offset and size to the one given, and rebuilds and repositions
	    the display axes (orientation compass), scene border and lights */
	public
	void ResizeScene(final float[] sOffset, final float[] sSize)
	{
		if (sOffset.length != sceneOffset.length)
			throw new RuntimeException("New scene offset of incompatible dimension.");
		if (sSize.length != sceneSize.length)
			throw new RuntimeException("New scene size of incompatible dimension.");

		//update the internal size information
		for (int d = 0; d < sceneSize.length; ++d)
		{
			sceneOffset[d] = sOffset[d];
			sceneSize[d]   = sSize[d];
		}

		adaptSceneBBoxAndCentreNode();
		scene.setNeedsUpdate(true);

		if (fixedLightsChoosen != fixedLightsState.NOTUSED)
		{
			fixedLightsState backupLightsState = fixedLightsChoosen;
			RemoveFixedLightsRamp();
			CreateFixedLightsRamp();
			while (ToggleFixedLights() != backupLightsState) ;
		}

		if (borderData != null) borderData.shapeForThisScene(sceneOffset,sceneSize);
		if ( axesData  != null)   axesData.shapeForThisScene(sceneOffset,sceneSize);
	}

	private
	void adaptSceneBBoxAndCentreNode()
	{
		final float[] halfBox = new float[]{
			-DsFactor * (sceneOffset[0] + 0.5f*sceneSize[0]),
			-DsFactor * (sceneOffset[1] + 0.5f*sceneSize[1]),
			-DsFactor * (sceneOffset[2] + 0.5f*sceneSize[2]) };
		scene.setPosition(halfBox);
	}
	//----------------------------------------------------------------------------


	protected SceneAxesData axesData = null;
	protected boolean axesShown = false;

	public
	void CreateDisplayAxes()
	{
		//remove any old axes, if they exist at all...
		RemoveDisplayAxes();

		axesData = new SceneAxesData();
		axesData.shapeForThisScene(sceneOffset,sceneSize);
		axesData.setMaterial(materials);

		axesData.parentNode = new Node("Scene orientation compass");
		axesData.becomeChildOf(axesData.parentNode);
		scene.addChild(axesData.parentNode);
		axesData.parentNode.setVisible(axesShown);
		//NB: set visibility as the last so that it can propagate to
		//all children (and make them synchronized w.r.t. visibility)
	}

	public
	void RemoveDisplayAxes()
	{
		axesShown = false;
		if (axesData == null) return;
		if (axesData.parentNode != null)
			scene.removeChild(axesData.parentNode);

		axesData = null;
	}

	public
	boolean ToggleDisplayAxes()
	{
		//first run, init the data
		if (axesData == null)
		{
			System.out.println("Creating compass axes before turning them on...");
			CreateDisplayAxes();
		}

		//toggle the flag
		axesShown ^= true;

		//adjust the visibility
		axesData.parentNode.setVisible(axesShown);

		return axesShown;
	}

	public
	boolean IsSceneAxesVisible()
	{ return axesShown; }
	//----------------------------------------------------------------------------


	protected SceneBorderData borderData = null;
	protected boolean borderShown = false;

	public
	void CreateDisplaySceneBorder()
	{
		//remove any old border, if it exists at all...
		RemoveDisplaySceneBorder();

		borderData = new SceneBorderData();
		borderData.shapeForThisScene(sceneOffset,sceneSize);
		borderData.setMaterial(materials);

		borderData.parentNode = new Node("Scene border frame");
		borderData.becomeChildOf(borderData.parentNode);
		scene.addChild(borderData.parentNode);
		borderData.parentNode.setVisible(borderShown);
		//NB: set visibility as the last so that it can propagate to
		//all children (and make them synchronized w.r.t. visibility)
	}

	public
	void RemoveDisplaySceneBorder()
	{
		borderShown = false;
		if (borderData == null) return;
		if (borderData.parentNode != null)
			scene.removeChild(borderData.parentNode);

		borderData = null;
	}

	public
	boolean ToggleDisplaySceneBorder()
	{
		//first run, init the data
		if (borderData == null)
		{
			System.out.println("Creating scene border before turning it on...");
			CreateDisplaySceneBorder();
		}

		//toggle the flag
		borderShown ^= true;

		//adjust the visibility
		borderData.parentNode.setVisible(borderShown);

		return borderShown;
	}

	public
	boolean IsSceneBorderVisible()
	{ return borderShown; }
	//----------------------------------------------------------------------------


	//the state flags of the lights
	public enum fixedLightsState { NOTUSED, NONE, BOTH, FRONT, REAR, CIRCLE }

	private PointLight[][] fixedLights = null;
	private fixedLightsState fixedLightsChoosen = fixedLightsState.NOTUSED;

	public
	void CreateFixedLightsRamp()
	{
		//remove any old lights, if they exist at all...
		RemoveFixedLightsRamp();

		//two light ramps at fixed positions
		//----------------------------------
		//elements of coordinates of positions of lights
		final float xLeft   = (sceneOffset[0] + 0.05f*sceneSize[0]) *DsFactor;
		final float xCentre = (sceneOffset[0] + 0.50f*sceneSize[0]) *DsFactor;
		final float xRight  = (sceneOffset[0] + 0.95f*sceneSize[0]) *DsFactor;

		final float yTop    = (sceneOffset[1] + 0.05f*sceneSize[1]) *DsFactor;
		final float yBottom = (sceneOffset[1] + 0.95f*sceneSize[1]) *DsFactor;

		final float zNear = (sceneOffset[2] + 1.3f*sceneSize[2]) *DsFactor;
		final float zFar  = (sceneOffset[2] - 0.3f*sceneSize[2]) *DsFactor;
		final float zNearCentre = (sceneOffset[2] + 1.5f*sceneSize[2]) *DsFactor;
		final float zFarCentre  = (sceneOffset[2] - 0.5f*sceneSize[2]) *DsFactor;

		//one light circle around the scene
		//----------------------------------
		final int noOfCircleLights_mid = 12;
		final int noOfCircleLights_side = 6;
		final float yCentre = (sceneOffset[1] + 0.50f*sceneSize[1]) *DsFactor;
		final float zCentre = (sceneOffset[2] + 0.50f*sceneSize[2]) *DsFactor;

		//tuned such that, given current light intensity and fading, the rear cells are dark yet visible
		final float radius = 1.1f*sceneSize[1] *DsFactor;

		//create the lights, one for each upper corner of the scene
		fixedLights = new PointLight[][] { new PointLight[6], new PointLight[6],
				new PointLight[2*noOfCircleLights_side + noOfCircleLights_mid] };
		(fixedLights[0][0] = new PointLight(radius)).setPosition(new Vector3f(xLeft  ,yTop   ,zNear));
		(fixedLights[0][1] = new PointLight(radius)).setPosition(new Vector3f(xLeft  ,yBottom,zNear));
		(fixedLights[0][2] = new PointLight(radius)).setPosition(new Vector3f(xCentre,yTop   ,zNearCentre));
		(fixedLights[0][3] = new PointLight(radius)).setPosition(new Vector3f(xCentre,yBottom,zNearCentre));
		(fixedLights[0][4] = new PointLight(radius)).setPosition(new Vector3f(xRight ,yTop   ,zNear));
		(fixedLights[0][5] = new PointLight(radius)).setPosition(new Vector3f(xRight ,yBottom,zNear));

		(fixedLights[1][0] = new PointLight(radius)).setPosition(new Vector3f(xLeft  ,yTop   ,zFar));
		(fixedLights[1][1] = new PointLight(radius)).setPosition(new Vector3f(xLeft  ,yBottom,zFar));
		(fixedLights[1][2] = new PointLight(radius)).setPosition(new Vector3f(xCentre,yTop   ,zFarCentre));
		(fixedLights[1][3] = new PointLight(radius)).setPosition(new Vector3f(xCentre,yBottom,zFarCentre));
		(fixedLights[1][4] = new PointLight(radius)).setPosition(new Vector3f(xRight ,yTop   ,zFar));
		(fixedLights[1][5] = new PointLight(radius)).setPosition(new Vector3f(xRight ,yBottom,zFar));

		fixedLights[0][0].setName("PointLight Ramp1: x-left, y-bottom, z-near");
		fixedLights[0][1].setName("PointLight Ramp1: x-left, y-top, z-near");
		fixedLights[0][2].setName("PointLight Ramp1: x-centre, y-bottom, z-near");
		fixedLights[0][3].setName("PointLight Ramp1: x-centre, y-top, z-near");
		fixedLights[0][4].setName("PointLight Ramp1: x-right, y-bottom, z-near");
		fixedLights[0][5].setName("PointLight Ramp1: x-right, y-top, z-near");

		fixedLights[1][0].setName("PointLight Ramp2: x-left, y-bottom, z-far");
		fixedLights[1][1].setName("PointLight Ramp2: x-left, y-top, z-far");
		fixedLights[1][2].setName("PointLight Ramp2: x-centre, y-bottom, z-far");
		fixedLights[1][3].setName("PointLight Ramp2: x-centre, y-top, z-far");
		fixedLights[1][4].setName("PointLight Ramp2: x-right, y-bottom, z-far");
		fixedLights[1][5].setName("PointLight Ramp2: x-right, y-top, z-far");

		for (int i=0; i < noOfCircleLights_mid; ++i)
		{
			final double ang = 2.0 * Math.PI * i / noOfCircleLights_mid;
			final double dx = Math.cos(ang) * 0.8 * sceneSize[0] * DsFactor;
			final double dz = Math.sin(ang) * 0.8 * sceneSize[2] * DsFactor;
			//
			(fixedLights[2][i] = new PointLight(radius)).setPosition(
				new Vector3f( xCentre + (float)dx, yCentre, zCentre + (float)dz ));
			fixedLights[2][i].setName("PointLight mid-Circle at "+(360*i/noOfCircleLights_mid)+" deg");
		}
		final double ang_offset = Math.PI / noOfCircleLights_side;
		for (int i=0; i < noOfCircleLights_side; ++i)
		{
			final double ang = (2.0 * Math.PI * i / noOfCircleLights_side) +ang_offset;
			final double dx = Math.cos(ang) * 0.4 * sceneSize[0] * DsFactor;
			final double dz = Math.sin(ang) * 0.4 * sceneSize[2] * DsFactor;
			final double dy = 0.866 * 0.8 * sceneSize[1] * DsFactor;
			//
			(fixedLights[2][noOfCircleLights_mid+ 2*i +0] = new PointLight(radius)).setPosition(
				new Vector3f( xCentre + (float)dx, yCentre - (float)dy, zCentre + (float)dz ));
			fixedLights[2][noOfCircleLights_mid+ 2*i +0].setName("PointLight low-Circle at "+((180+360*i)/noOfCircleLights_side)+" deg");
			//
			(fixedLights[2][noOfCircleLights_mid+ 2*i +1] = new PointLight(radius)).setPosition(
				new Vector3f( xCentre + (float)dx, yCentre + (float)dy, zCentre + (float)dz ));
			fixedLights[2][noOfCircleLights_mid+ 2*i +1].setName("PointLight high-Circle at "+((180+360*i)/noOfCircleLights_side)+" deg");
		}

		//common settings of all lights
		final Vector3f lightsColor = new Vector3f(1.0f, 1.0f, 1.0f);
		for (PointLight[] lightRamp : fixedLights)
			for (PointLight l : lightRamp)
			{
				l.setIntensity(50.0f*DsFactor);
				l.setEmissionColor(lightsColor);
				l.setVisible(false);
				sciView.addNode(l);
			}

		fixedLightsChoosen = fixedLightsState.NONE;

		/* ENABLE THIS TO HAVE A SMALL SPHERES PLACED WHERE THE LIGHTS ARE */
		/*
		for (PointLight[] lightRamp : fixedLights)
			for (PointLight l : lightRamp)
				l.addChild( new Sphere(1.0f, 12) );
		*/
	}

	public
	void RepositionFixedLightsRamp(final float newDsFactor)
	{
		if (fixedLights == null) return;

		final float correction = newDsFactor / DsFactor;

		for (PointLight[] lightRamp : fixedLights)
			for (PointLight l : lightRamp)
			{
				l.setLightRadius( l.getLightRadius()*correction );
				l.setIntensity( l.getIntensity()*correction );
				l.getPosition().mul( correction );
				l.setNeedsUpdate(true);
			}
	}

	public
	void RemoveFixedLightsRamp()
	{
		if (fixedLights == null) return;

		for (PointLight[] lightRamp : fixedLights)
			for (PointLight l : lightRamp)
				sciView.deleteNode(l);

		fixedLights = null;
		fixedLightsChoosen = fixedLightsState.NONE;
	}

	public
	fixedLightsState ToggleFixedLights()
	{
		if (fixedLights == null && fixedLightsChoosen != fixedLightsState.NOTUSED)
		{
			System.out.println("Creating light ramps before turning them on...");
			CreateFixedLightsRamp();
		}

		switch (fixedLightsChoosen)
		{
		case NONE:
			for (PointLight l : fixedLights[0]) l.setVisible(true);
			for (PointLight l : fixedLights[1]) l.setVisible(true);
			for (PointLight l : fixedLights[2]) l.setVisible(false);
			fixedLightsChoosen = fixedLightsState.BOTH;
			break;
		case BOTH:
			for (PointLight l : fixedLights[0]) l.setVisible(true);
			for (PointLight l : fixedLights[1]) l.setVisible(false);
			for (PointLight l : fixedLights[2]) l.setVisible(false);
			fixedLightsChoosen = fixedLightsState.FRONT;
			break;
		case FRONT:
			for (PointLight l : fixedLights[0]) l.setVisible(false);
			for (PointLight l : fixedLights[1]) l.setVisible(true);
			for (PointLight l : fixedLights[2]) l.setVisible(false);
			fixedLightsChoosen = fixedLightsState.REAR;
			break;
		case REAR:
			for (PointLight l : fixedLights[0]) l.setVisible(false);
			for (PointLight l : fixedLights[1]) l.setVisible(false);
			for (PointLight l : fixedLights[2]) l.setVisible(true);
			fixedLightsChoosen = fixedLightsState.CIRCLE;
			break;
		case CIRCLE:
			for (PointLight l : fixedLights[0]) l.setVisible(false);
			for (PointLight l : fixedLights[1]) l.setVisible(false);
			for (PointLight l : fixedLights[2]) l.setVisible(false);
			fixedLightsChoosen = fixedLightsState.NONE;
			break;
		}

		//report the current state
		return fixedLightsChoosen;
	}

	public
	float IncreaseFixedLightsIntensity()
	{
		if (fixedLights == null) return 0.f;

		final float curInt = fixedLights[0][0].getIntensity() + 0.2f;

		for (PointLight[] lightRamp : fixedLights)
			for (PointLight l : lightRamp)
				l.setIntensity(curInt);

		return curInt;
	}

	public
	float DecreaseFixedLightsIntensity()
	{
		if (fixedLights == null) return 0.f;

		final float curInt = Math.max(fixedLights[0][0].getIntensity() - 0.2f, 0.1f);

		for (PointLight[] lightRamp : fixedLights)
			for (PointLight l : lightRamp)
				l.setIntensity(curInt);

		return curInt;
	}

	public
	fixedLightsState ReportChosenFixedLights()
	{ return fixedLightsChoosen; }

	public
	boolean IsFixedLightsAvailable()
	{ return (fixedLights != null); }
	//----------------------------------------------------------------------------


	/** flag for external modules to see if they should call saveNextScreenshot() */
	public boolean savingScreenshots = false;

	/** flag for external modules to see if they should call saveNextScreenshot() */
	public String savingScreenshotsFilename = "/tmp/frame%04d.png";

	/** helper method to save the current content of the scene into /tmp/frameXXXX.png */
	public
	void saveNextScreenshot()
	{
		final String filename = String.format(savingScreenshotsFilename,tickCounter);
		System.out.println("Saving screenshot: "+filename);
		sciView.getSceneryRenderer().screenshot(filename,true);
	}

	/** counts how many times the "tick message" has been received, this message
	    is assumed to be sent typically after one simulation round is over */
	int tickCounter = 0;
	//
	public
	void increaseTickCounter()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		++tickCounter;
	 }
	}
	//----------------------------------------------------------------------------


	/** A handle on a lock (synchronization subject) that shall be used when a caller
	    wants to modify any of the pointNodes, lineNodes, vectorNodes or tickCounter.

	    Since the DisplayScene's API for updating the displayed graphics was not
	    designed for concurrent access, the callers have to synchronize explicitly
	    among themselves. And they shall do it precisely around this attribute. */
	public final Object lockOnChangingSceneContent = new Object();

	/** these points are registered with the display, but not necessarily always visible */
	final Map<Integer,Point> pointNodes = new HashMap<>();
	/** these lines are registered with the display, but not necessarily always visible */
	final Map<Integer,Line> lineNodes = new HashMap<>();
	/** these vectors are registered with the display, but not necessarily always visible */
	final Map<Integer,VectorSH> vectorNodes = new HashMap<>();


	/** this is designed (yet only) for SINGLE-THREAD application! */
	public
	void addUpdateOrRemovePoint(final int ID,final Point p)
	{
		//intentionally empty
	}

	/** this is designed (yet only) for SINGLE-THREAD application! */
	public
	void addUpdateOrRemoveLine(final int ID,final Line l)
	{
		//intentionally empty
	}

	/** this is designed (yet only) for SINGLE-THREAD application! */
	public
	void addUpdateOrRemoveVector(final int ID,final Vector v)
	{
		//intentionally empty
	}


	public
	void removeAllObjects()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		garbageCollect(Integer.MIN_VALUE);
	 }
	}

	public
	void garbageCollect()
	{
		garbageCollect(0);
	}

	/** remove all objects that were last touched before this.tickCounter-tolerance */
	public
	void garbageCollect(int tolerance)
	{
		//intentionally empty
	}
	//
	/** flag for external modules to see if they should call garbageCollect() */
	public boolean garbageCollecting = true;
	//----------------------------------------------------------------------------


	public
	void suspendNodesUpdating()
	{
		//intentionally empty
	}

	public
	void resumeNodesUpdating()
	{
		//intentionally empty
	}
	//----------------------------------------------------------------------------


	/** cell forces are typically small in magnitude compared to the cell size,
	    this defines the current magnification applied when displaying the force vectors */
	float vectorsStretch = 1.f;

	float getVectorsStretch()
	{ return vectorsStretch; }

	void setVectorsStretch(final float vs)
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		//update the stretch factor...
		vectorsStretch = vs;

		//...and rescale all vectors presently existing in the system
		vectorNodes.values().forEach( n -> {
			n.applyScale(vectorsStretch,vec_headLengthRatio);
			n.node.updateWorld(false,true);
			n.nodeHead.updateWorld(false,true);
		} );
	 }
	}
	//----------------------------------------------------------------------------


	/** groups visibility conditions across modes for one displayed object, e.g. sphere */
	private static class elementVisibility
	{
		public boolean g_Mode = true;   //the cell debug mode, operated with 'g' key
		public boolean G_Mode = true;   //the global purpose debug mode, operated with 'G'
	}

	/** signals if we want to have cells (points aka spheres) displayed (even if cellsData is initially empty) */
	elementVisibility spheresShown = new elementVisibility();

	/** signals if we want to have cell lines displayed */
	elementVisibility linesShown = new elementVisibility();

	/** signals if we want to have cell forces (vectors) displayed */
	elementVisibility vectorsShown = new elementVisibility();

	/** signals if we want to have cell "debugging" elements displayed */
	private boolean cellDebugShown = false;

	/** signals if we want to have general purpose "debugging" elements displayed */
	private boolean generalDebugShown = false;

	public boolean IsCellDebugShown()    { return cellDebugShown; }
	public boolean IsGeneralDebugShown() { return cellDebugShown; }

	public boolean IsCellSpheresShown() { return spheresShown.g_Mode; }
	public boolean IsCellLinesShown()   { return linesShown.g_Mode; }
	public boolean IsCellVectorsShown() { return vectorsShown.g_Mode; }

	public boolean IsGeneralSpheresShown() { return spheresShown.G_Mode; }
	public boolean IsGeneralLinesShown()   { return linesShown.G_Mode; }
	public boolean IsGeneralVectorsShown() { return vectorsShown.G_Mode; }


	public
	boolean ToggleDisplayCellSpheres()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		//toggle the flag
		spheresShown.g_Mode ^= true;

		//sync expected_* constants with current state of visibility flags
		//apply the new setting on the points
		for (Integer ID : pointNodes.keySet())
			showOrHideMe(ID,pointNodes.get(ID).node,spheresShown);

		return spheresShown.g_Mode;
	 }
	}

	public
	boolean ToggleDisplayCellLines()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		linesShown.g_Mode ^= true;

		for (Integer ID : lineNodes.keySet())
			showOrHideMe(ID,lineNodes.get(ID).node,linesShown);

		return linesShown.g_Mode;
	 }
	}

	public
	boolean ToggleDisplayCellVectors()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		vectorsShown.g_Mode ^= true;

		for (Integer ID : vectorNodes.keySet())
			//showOrHideMe(ID,vectorNodes.get(ID).node,vectorsShown);
			showOrHideMeForVectorSH(ID);

		return vectorsShown.g_Mode;
	 }
	}

	public
	boolean ToggleDisplayCellDebug()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		cellDebugShown ^= true;

		//"debug" objects might be present in any shape primitive
		for (Integer ID : pointNodes.keySet())
			showOrHideMe(ID,pointNodes.get(ID).node,spheresShown);
		for (Integer ID : lineNodes.keySet())
			showOrHideMe(ID,lineNodes.get(ID).node,linesShown);
		for (Integer ID : vectorNodes.keySet())
			//showOrHideMe(ID,vectorNodes.get(ID).node,vectorsShown);
			showOrHideMeForVectorSH(ID);

		return cellDebugShown;
	 }
	}


	public
	boolean ToggleDisplayGeneralDebugSpheres()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		//toggle the flag
		spheresShown.G_Mode ^= true;

		//sync expected_* constants with current state of visibility flags
		//apply the new setting on the points
		for (Integer ID : pointNodes.keySet())
			showOrHideMe(ID,pointNodes.get(ID).node,spheresShown);

		return spheresShown.G_Mode;
	 }
	}

	public
	boolean ToggleDisplayGeneralDebugLines()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		linesShown.G_Mode ^= true;

		for (Integer ID : lineNodes.keySet())
			showOrHideMe(ID,lineNodes.get(ID).node,linesShown);

		return linesShown.G_Mode;
	 }
	}

	public
	boolean ToggleDisplayGeneralDebugVectors()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		vectorsShown.G_Mode ^= true;

		for (Integer ID : vectorNodes.keySet())
			//showOrHideMe(ID,vectorNodes.get(ID).node,vectorsShown);
			showOrHideMeForVectorSH(ID);

		return vectorsShown.G_Mode;
	 }
	}

	public
	boolean ToggleDisplayGeneralDebug()
	{
	 synchronized (lockOnChangingSceneContent)
	 {
		generalDebugShown ^= true;

		//"debug" objects might be present in any shape primitive
		for (Integer ID : pointNodes.keySet())
			showOrHideMe(ID,pointNodes.get(ID).node,spheresShown);
		for (Integer ID : lineNodes.keySet())
			showOrHideMe(ID,lineNodes.get(ID).node,linesShown);
		for (Integer ID : vectorNodes.keySet())
			//showOrHideMe(ID,vectorNodes.get(ID).node,vectorsShown);
			showOrHideMeForVectorSH(ID);

		return generalDebugShown;
	 }
	}


	public
	void EnableFrontFaceCulling()
	{
		materials.EnableFrontFaceCulling();
	}

	public
	void DisableFrontFaceCulling()
	{
		materials.DisableFrontFaceCulling();
	}
	//----------------------------------------------------------------------------


	//ID space of graphics primitives: 31 bits
	//
	//     lowest 16 bits: ID of the graphics element itself
	//next lowest  1 bit : proper (=0) or debug (=1) element
	//next lowest 14 bits: "identification" of ONE single cell
	//     highest 1 bit : not used (sign bit)
	//
	//note: general purpose elements have cell "identification" equal to 0
	//      in which case the debug bit is not applied
	//note: there are 4 graphics primitives: points, lines, vectors, meshes;
	//      each is living in its own list of elements (e.g. this.pointNodes)

	//constants to "read out" respective information
	@SuppressWarnings("unused")
	static final int MASK_ELEM   = ((1 << 16)-1);
	static final int MASK_DEBUG  =   1 << 16;
	static final int MASK_CELLID = ((1 << 14)-1) << 17;

	/** given the current display preference in 'displayFlag',
	    the visibility of the object 'n' with ID is adjusted,
	    the decided state is indicated in the return value */
	boolean showOrHideMe(final int ID, final Node n, final elementVisibility displayFlag)
	{
		boolean vis = false;
		if ((ID & MASK_CELLID) == 0)
		{
			//the ID does not belong to any cell, still the (filtering) flags apply
			vis = displayFlag.G_Mode == true ? generalDebugShown : displayFlag.G_Mode;
		}
		else
		{
			vis = displayFlag.g_Mode == true && (ID & MASK_DEBUG) > 0 ?
				cellDebugShown : displayFlag.g_Mode;

			//NB: follows this table
			// flag  MASK_DEBUG   cellDebugShown    result
			// true    1          true              true
			// true    1          false             false
			// true    0          true              true
			// true    0          false             true

			// false   1          true              false
			// false   1          false             false
			// false   0          true              false
			// false   0          false             false
		}

		if (n != null) n.setVisible(vis);
		return vis;
	}

	void showOrHideMeForVectorSH(final int ID)
	{
		final VectorSH v = vectorNodes.get(ID);
		if (v == null)
			throw new RuntimeException("Invalid vector ID given (ID="+ID+")");

		v.nodeHead.setVisible( showOrHideMe(ID,v.node,vectorsShown) );
		//NB: sets the same visibility to both nodes, see few lines above
	}

	String createNodeName(final int ID)
	{
		if ((ID & MASK_CELLID) == 0) return ("Global debug "+(ID & MASK_ELEM));
		if ((ID & MASK_DEBUG) > 0)   return ((ID >> 17)+" cell's debug "+(ID & MASK_ELEM));
		return ((ID >> 17)+" cell's "+(ID & MASK_ELEM));
	}
	//----------------------------------------------------------------------------


	public
	void reportSettings()
	{
		reportSettings(System.out);
	}

	public
	void reportSettings(final PrintStream m)
	{
		m.println("------------- SimViewer's current status: -------------");
		m.println("push mode       : " + sciView.getPushMode() + "  \tscreenshots            : " + savingScreenshots);
		m.println("garbage collect.: " + garbageCollecting     + "  \ttickCounter            : " + tickCounter);
		m.println("scene lights    : " + fixedLightsChoosen    + "  \tscreenshots path       : " + savingScreenshotsFilename);
		m.println("scene border    : " + borderShown           + "  \torientation compass    : " + axesShown);
		m.println("scene offset    : " + sceneOffset[0]+","+sceneOffset[1]+","+sceneOffset[2]+" microns");
		m.println("scene size      : " + sceneSize[0]  +","+sceneSize[1]  +","+sceneSize[2]  +" microns");
		m.println("visibility      : 'g' 'G'"                                                               +  "\t'g' mode   (cell debug): " + cellDebugShown);
		m.println("         points :  "+(spheresShown.g_Mode? "Y":"N")+"   "+(spheresShown.G_Mode? "Y":"N") + " \t'G' mode (global debug): " + generalDebugShown);
		m.println("         lines  :  "+(  linesShown.g_Mode? "Y":"N")+"   "+(  linesShown.G_Mode? "Y":"N") + " \tvector elongation      : " + vectorsStretch + "x");
		m.println("         vectors:  "+(vectorsShown.g_Mode? "Y":"N")+"   "+(vectorsShown.G_Mode? "Y":"N") + " \tfront faces culling    : " + IsFrontFacesCullingEnabled());

		m.println("number of points: " + this.pointNodes.size() + "\t  lines: "+this.lineNodes.size() + "\t  vectors: "+this.vectorNodes.size());
		m.println("color legend    :        white: velocity, 1stInnerMost2Yolk");
		m.println(" red: overlap            green: cell&skeleton          blue: friction, skelDev");
		m.println("cyan: body             magenta: tracks, rep&drive    yellow: slide, 2ndInnerMost2Yolk, tracksFF");
	}
	//----------------------------------------------------------------------------


	public static
	void rotateNodeToDir(final Node node, final Vector3f newDir)
	{
		node.getRotation().identity().rotateTo(initialDir, newDir);
	}

	private static final Vector3f initialDir = new Vector3f(0,1,0);
	//----------------------------------------------------------------------------


	/** reference on the currently available FlightRecording: the object
	    must initialized outside and reference on it is given here, otherwise
	    the reference must be null */
	CommandFromFlightRecorder flightRecorder = null;

	private class BehaviourForFlightRecorder implements ClickBehaviour
	{
		BehaviourForFlightRecorder(final char key) { actionKey = key; }
		final char actionKey;

		@Override
		public void click( final int x, final int y )
		{
			if (flightRecorder != null)
			{
				try {
					boolean status = false;
					switch (actionKey)
					{
					case '7':
						status = flightRecorder.rewindAndSendFirstTimepoint();
						break;
					case '8':
						status = flightRecorder.sendPrevTimepointMessages();
						break;
					case '9':
						status = flightRecorder.sendNextTimepointMessages();
						break;
					case '0':
						status = flightRecorder.rewindAndSendLastTimepoint();
						break;
					}

					if (!status)
						System.out.println("No FlightRecording file is opened.");
				}
				catch (InterruptedException e) {
					System.out.println("DisplayScene: Interrupted and stopping...");
					stop();
				}
			}
			else System.out.println("FlightRecording is not available.");
		}
	}
}
