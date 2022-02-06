/*
 * $Id$
 * 
 * Copyright (c) 2021, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.simsilica.demo.sim.ai;

import java.io.*;

import org.slf4j.*;

import com.google.common.base.MoreObjects;

import com.simsilica.mathd.*;

/**
 *  Keeps track of an octree of byte values that can be queried for 
 *  intersection.  This is probably a temporary location until the
 *  class gets more formalized.
 *
 *  @author    Paul Speed
 */
public class OctBytes {
    static Logger log = LoggerFactory.getLogger(OctBytes.class);
    
    private Vec3d origin;
    private double minSize;
    private double maxSize;
    private double scale;
    private double invScale;
    
    private Octad root;
    private Vec3i rootOrigin;
    private int rootSize;
    
    public OctBytes( Vec3d origin, double minSize, double maxSize ) {
        this.origin = origin;
        this.minSize = minSize;
        this.maxSize = maxSize;
        
        // To avoid ambiguity we treat the minimum size as 1 and
        // scale up/down as necessary.  This means that all coordinates
        // are represented as integers internally and we don't have to
        // worry about subtle rounding errors.
        this.scale = 1/minSize;
        this.invScale = 1/scale;
        this.rootSize = (int)(maxSize * scale);
        this.rootOrigin = origin.mult(scale).floor();
        this.root = new Octad();
    }

    public void set( Vec3d min, Vec3d max, byte value ) {
//log.info("set(" + min + ", " + max + ", " + value + ")");    
        Vec3i v1 = min.mult(scale).floor();
        Vec3i v2 = max.mult(scale).floor();
//log.info("v1:" + v1 + " v2:" + v2);
        root.set(rootOrigin.x, rootOrigin.y, rootOrigin.z, rootSize, v1, v2, value);
    }   
    
    public void clear( Vec3d min, Vec3d max ) {
        set(min, max, (byte)0x0);    
    } 
 
    public boolean intersects( Vec3d min, Vec3d max ) {
        return false;
    } 

    public String dump() {
        StringWriter result = new StringWriter();
        dump(new PrintWriter(result));
        return result.toString();   
    }
    
    public void dump( PrintWriter out ) {
        out.println(this);
        root.dump("", "root", out);
    }

    // Returns true if the octad defined by the specified parameters
    // is completely contained in the box defined by min,max
    private static boolean octadInBox( int xo, int yo, int zo, int size, Vec3i min, Vec3i max ) {
//log.info("octadInBox(" + xo + ", " + yo + ", " + zo + ", " + size + ", " + min + ", " + max + ")");    
        // Note: we use <= beceause we are also using size to define
        // the octad bounds.  Not sure if min/max will be max-inclusive yet or not.
        // So this may need to change... but it's likely that max will also be min+size.
        if( xo >= min.x && yo >= min.y && zo >= min.z
            && xo + size <= max.x && yo + size <= max.y && zo + size <= max.z ) {
//log.info("   yes");            
            return true;
        }
//log.info("   no");            
        return false;
    }
    
    private static boolean octadOutsideBox( int xo, int yo, int zo, int size, Vec3i min, Vec3i max ) {
//log.info("octadOutsideBox(" + xo + ", " + yo + ", " + zo + ", " + size + ", " + min + ", " + max + ")");    
        if( xo + size <= min.x || yo + size <= min.y || zo + size <= min.z ) {
//log.info("    fully below the box");        
            return true;
        }
        // If max is exclusive then we need >=
        if( xo >= max.x || yo >= max.y || zo >= max.z ) {
//log.info("    fully above the box");        
            return true;
        }
//log.info("   no");            
        // Some part overlaps
        return false; 
    } 
 
    public OctCell getDebugView() {
        return new OctCell(root, rootOrigin, rootSize);
    }
 
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass().getSimpleName())
            .add("origin", origin)
            .add("minSisze", minSize)
            .add("maxSize", maxSize)
            .toString();
    }
 
    /**
     *  An expanded read-only debug view of the octree.
     */
    public class OctCell {
        private Octad octad;
        private Vec3i origin;
        private int size;
        private byte value;
        private OctCell[] children;
        
        private OctCell( Octad octad, Vec3i origin, int size ) {
            this.octad = octad;
            this.origin = origin;
            this.size = size;
            this.value = octad.value;
            
            if( octad.children != null ) {
                children = new OctCell[8];
                int split = size>>1;
                children[0] = new OctCell(octad.children[0], origin.add(0,     0,     0),     split);
                children[1] = new OctCell(octad.children[1], origin.add(split, 0,     0),     split);
                children[2] = new OctCell(octad.children[2], origin.add(0,     split, 0),     split);
                children[3] = new OctCell(octad.children[3], origin.add(split, split, 0),     split);
                children[4] = new OctCell(octad.children[4], origin.add(0,     0,     split), split);
                children[5] = new OctCell(octad.children[5], origin.add(split, 0,     split), split);
                children[6] = new OctCell(octad.children[6], origin.add(0,     split, split), split);
                children[7] = new OctCell(octad.children[7], origin.add(split, split, split), split);
            }
        }
        
        public Vec3d getOrigin() {
            Vec3d result = origin.toVec3d().multLocal(invScale);
            return result;
        }
        
        public double getSize() {
            return size * invScale;
        }
        
        public Byte getValue() {
            // We only really have a value if we have no children
            return children != null ? null : value;
        }
        
        public OctCell[] getChildren() {
            return children;
        }
        
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(getClass().getSimpleName())
                .add("origin", getOrigin())
                .add("size", getSize())
                .add("value", getValue())
                .toString(); 
        }        
    }
 
    private static class Octad {
        public byte value;
        public Octad[] children;

        public Octad() {
        }

        public void dump( String indent, String label, PrintWriter out ) {
            if( children == null ) {
                out.println(indent + label + ":" + value);
                return;
            }
            out.println(indent + label + ":");
            for( int i = 0; i < 8; i++ ) {
                int x = i % 2;
                int y = (i / 2) % 2;
                int z = (i / 4) % 2;
                children[i].dump(indent + "  ", "[" + x + "][" + y + "][" + z + "]", out);
            } 
        }

        private void split() {
            children = new Octad[8];
            for( int i = 0; i < 8; i++ ) {
                children[i] = new Octad();
            } 
        }

        // Returns true if totally set to value, ie: no children       
        public boolean set( int xo, int yo, int zo, int size, Vec3i min, Vec3i max, byte value ) {
//log.info("fill(" + xo + ", " + yo + ", " + zo + ", " + size + ", " + min + ", " + max + ")");
            if( octadOutsideBox(xo, yo, zo, size, min, max) ) {
//log.info("  fully outside");            
                // Then don't change anything... but if we have no children
                // and match the value desired then return true.  This won't 
                // help with all collapse cases but it will help with the simplest ones.
                // There could be cases where we have children but are completely outside
                // the box... but I guess if we have children then this octad is already
                // not fully one value.  So maybe this does cover all cases.
                if( children == null ) {
                    return this.value == value;
                }
                return false;
            }
            // We are either fully contained in or partially overlap
            // the box.
            
            if( children == null ) {
                // Are we completely inside the box?
                if( octadInBox(xo, yo, zo, size, min, max) ) {
//log.info("  fully inside");            
                    // Fill us and we're done... no reason to split further
                    this.value = value;
                    return true;
                }
                if( size == 1 ) {
                    // We cannot split further and are somehow still partially
                    // in the box.
                    log.error("Not outside. Not inside. Split is 0. origin:" + xo + ", " + yo + ", " + zo + "  size:" + size + "  min:" + min + ", max:" + max);
                    return false;
                }
                // Else we need to split
                split();
            }
            // We will always have children by this point
            int count = 0;
            
            // Order has to be consistent in all of these types of blocks since
            // we supply the octad coordinates externally
            int split = size >> 1;
            if( children[0].set(xo,       yo,       zo,        split, min, max, value) ) count++;
            if( children[1].set(xo+split, yo,       zo,        split, min, max, value) ) count++;
            if( children[2].set(xo,       yo+split, zo,        split, min, max, value) ) count++;
            if( children[3].set(xo+split, yo+split, zo,        split, min, max, value) ) count++;
            if( children[4].set(xo,       yo,       zo+split,  split, min, max, value) ) count++;
            if( children[5].set(xo+split, yo,       zo+split,  split, min, max, value) ) count++;
            if( children[6].set(xo,       yo+split, zo+split,  split, min, max, value) ) count++;
            if( children[7].set(xo+split, yo+split, zo+split,  split, min, max, value) ) count++;
 
            if( count == 8 ) {
                // Then we don't need children
                children = null;
                this.value = value;
                return true;
            }
            return false;           
        }
 
    }
}

//if( children != null ) {
//    check children
//} else {
//    return filled;
//}





