/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "fdlibm.h"

/*
 * This file contains all the native methods of java.lang.StrictMath. We use the same library as the
 * standard JDK: fdlibm in the subdirectory. The library code is copied without modification from
 * JDK 7 update 40, src/share/native/java/lang/fdlibm. This file is modified from the original
 * StrictMath.c file to exclude all the JNI-specific method parameters. We use method names prefixed
 * with "StrictMath_" to avoid confusion with method names in the standard C library.
 */


#define JNIEXPORT
#define JNICALL
#define jdouble double

JNIEXPORT jdouble JNICALL
StrictMath_cos(jdouble d)
{
    return (jdouble) cos((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_sin(jdouble d)
{
    return (jdouble) sin((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_tan(jdouble d)
{
    return (jdouble) tan((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_asin(jdouble d)
{
    return (jdouble) asin((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_acos(jdouble d)
{
    return (jdouble) acos((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_atan(jdouble d)
{
    return (jdouble) atan((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_exp(jdouble d)
{
    return (jdouble) exp((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_log(jdouble d)
{
    return (jdouble) log((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_log10(jdouble d)
{
    return (jdouble) log10((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_sqrt(jdouble d)
{
    return (jdouble) sqrt((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_cbrt(jdouble d)
{
    return (jdouble) cbrt((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_atan2(jdouble d1, jdouble d2)
{
    return (jdouble) atan2((double)d1, (double)d2);
}

JNIEXPORT jdouble JNICALL
StrictMath_pow(jdouble d1, jdouble d2)
{
    return (jdouble) pow((double)d1, (double)d2);
}

JNIEXPORT jdouble JNICALL
StrictMath_IEEEremainder(
                                  jdouble dividend,
                                  jdouble divisor)
{
    return (jdouble) remainder(dividend, divisor);
}

JNIEXPORT jdouble JNICALL
StrictMath_cosh(jdouble d)
{
    return (jdouble) cosh((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_sinh(jdouble d)
{
    return (jdouble) sinh((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_tanh(jdouble d)
{
    return (jdouble) tanh((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_hypot(jdouble x, jdouble y)
{
    return (jdouble) hypot((double)x, (double)y);
}



JNIEXPORT jdouble JNICALL
StrictMath_log1p(jdouble d)
{
    return (jdouble) log1p((double)d);
}

JNIEXPORT jdouble JNICALL
StrictMath_expm1(jdouble d)
{
    return (jdouble) expm1((double)d);
}
