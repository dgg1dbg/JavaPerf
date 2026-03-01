package org.example.seqlock;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

public final class UnsafeAccess {
    private static final Unsafe UNSAFE = lookupUnsafe();
    private static final long BYTE_ARRAY_BASE = UNSAFE.arrayBaseOffset(byte[].class);
    private static final long OBJECT_ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class);
    private static final int OBJECT_REF_SCALE = UNSAFE.arrayIndexScale(Object[].class);

    private UnsafeAccess() {
    }

    public static long byteArrayDataAddress(byte[] array) {
        return objectAddress(array) + BYTE_ARRAY_BASE;
    }

    private static long objectAddress(Object obj) {
        final Object[] holder = new Object[]{obj};
        if (OBJECT_REF_SCALE == 8) {
            return UNSAFE.getLong(holder, OBJECT_ARRAY_BASE);
        }
        if (OBJECT_REF_SCALE == 4) {
            // Typical compressed-oops layout in HotSpot.
            return (UNSAFE.getInt(holder, OBJECT_ARRAY_BASE) & 0xFFFF_FFFFL) << 3;
        }
        throw new IllegalStateException("Unsupported reference scale: " + OBJECT_REF_SCALE);
    }

    private static Unsafe lookupUnsafe() {
        try {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to acquire sun.misc.Unsafe", e);
        }
    }
}
