/*
 * Copyright 2020 Yury Kharchenko (original KEmulator implementation)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from KEmulator micro3d_gl into freej2me. Standard API shell using
 * base.MathUtil fixed-point trig.
 */
package com.mascotcapsule.micro3d.v3;

import com.mascotcapsule.micro3d.v3.base.MathUtil;

@SuppressWarnings({"unused"})
public class Util3D {

	public static int sqrt(int p) {
		return MathUtil.uSqrt(p);
	}

	public static int sin(int p) {
		return MathUtil.iSin(p);
	}

	public static int cos(int p) {
		return MathUtil.iCos(p);
	}
}
