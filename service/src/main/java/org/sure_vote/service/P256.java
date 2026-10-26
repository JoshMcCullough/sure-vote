package org.sure_vote.service;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;
import java.util.Objects;

/**
 * NIST P-256 points as uncompressed SEC1 bytes ({@code 04 || X || Y}, 65 bytes) and scalars as 32 big-endian bytes.
 * {@link #multiply} derives a public key from a private scalar for test, showcase, and reference-sealer keys only.
 * Production private keys stay on hardware and never reach this method.
 */
final class P256 {
    static final ECParameterSpec SPEC = spec();
    static final BigInteger P = field();
    static final BigInteger N = SPEC.getOrder();
    static final BigInteger HALF_N = N.shiftRight(1);
    static final int POINT_BYTES = 65;
    static final int SCALAR_BYTES = 32;

    private static final BigInteger A = SPEC
        .getCurve()
        .getA();
    private static final BigInteger B = SPEC
        .getCurve()
        .getB();
    private static final BigInteger TWO = BigInteger.TWO;
    private static final BigInteger THREE = BigInteger.valueOf(3);

    private P256() {
    }

    static byte[] encode(final ECPoint point) {
        final var out = new byte[POINT_BYTES];
        out[0] = 0x04;
        copyFixed(point.getAffineX(), out, 1);
        copyFixed(point.getAffineY(), out, 1 + SCALAR_BYTES);
        return out;
    }

    static ECPoint decode(final byte[] encoded) {
        if (encoded == null || encoded.length != POINT_BYTES || encoded[0] != 0x04) {
            return null;
        }
        else {
            final var x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 1 + SCALAR_BYTES));
            final var y = new BigInteger(1, Arrays.copyOfRange(encoded, 1 + SCALAR_BYTES, POINT_BYTES));
            return onCurve(x, y) ? new ECPoint(x, y) : null;
        }
    }

    static byte[] scalarBytes(final BigInteger value) {
        final var out = new byte[SCALAR_BYTES];
        copyFixed(value, out, 0);
        return out;
    }

    static BigInteger scalar(final byte[] bytes) {
        if (bytes == null || bytes.length != SCALAR_BYTES) {
            throw new IllegalArgumentException("a P-256 private key is 32 bytes");
        }
        else {
            final var value = new BigInteger(1, bytes);
            return checkedScalar(value);
        }
    }

    static byte[] publicKey(final byte[] privateScalar) {
        final var point = multiply(scalar(privateScalar), SPEC.getGenerator());
        return encode(point);
    }

    static ECPoint multiply(final BigInteger k, final ECPoint point) {
        var low = ECPoint.POINT_INFINITY;
        var high = point;
        for (var bit = N.bitLength() - 1; bit >= 0; bit = bit - 1) {
            if (k.testBit(bit)) {
                low = add(low, high);
                high = twice(high);
            }
            else {
                high = add(low, high);
                low = twice(low);
            }
        }
        return low;
    }

    private static BigInteger checkedScalar(final BigInteger value) {
        if (value.signum() <= 0 || value.compareTo(N) >= 0) {
            throw new IllegalArgumentException("a P-256 private key is between 1 and n - 1");
        }
        else {
            return value;
        }
    }

    private static boolean onCurve(final BigInteger x, final BigInteger y) {
        final var inField = x.compareTo(P) < 0 && y.compareTo(P) < 0;
        if (!inField) {
            return false;
        }
        else {
            final var left = mul(y, y);
            final var cube = mul(mul(x, x), x);
            final var right = plus(plus(cube, mul(A, x)), B);
            return left.equals(right);
        }
    }

    private static ECPoint add(final ECPoint left, final ECPoint right) {
        if (left.equals(ECPoint.POINT_INFINITY)) {
            return right;
        }
        else if (right.equals(ECPoint.POINT_INFINITY)) {
            return left;
        }
        else if (Objects.equals(left.getAffineX(), right.getAffineX())) {
            return same(left, right);
        }
        else {
            final var dy = minus(right.getAffineY(), left.getAffineY());
            final var dx = minus(right.getAffineX(), left.getAffineX());
            final var slope = mul(dy, dx.modInverse(P));
            return line(slope, left, right);
        }
    }

    private static ECPoint same(final ECPoint left, final ECPoint right) {
        if (Objects.equals(left.getAffineY(), right.getAffineY())) {
            return twice(left);
        }
        else {
            return ECPoint.POINT_INFINITY;
        }
    }

    private static ECPoint twice(final ECPoint point) {
        if (point.equals(ECPoint.POINT_INFINITY) || BigInteger.ZERO.equals(point.getAffineY())) {
            return ECPoint.POINT_INFINITY;
        }
        else {
            final var x = point.getAffineX();
            final var top = plus(mul(THREE, mul(x, x)), A);
            final var doubled = mul(TWO, point.getAffineY());
            final var slope = mul(top, doubled.modInverse(P));
            return line(slope, point, point);
        }
    }

    private static ECPoint line(final BigInteger slope, final ECPoint left, final ECPoint right) {
        final var x = minus(minus(mul(slope, slope), left.getAffineX()), right.getAffineX());
        final var y = minus(mul(slope, minus(left.getAffineX(), x)), left.getAffineY());
        return new ECPoint(x, y);
    }

    private static BigInteger mul(final BigInteger left, final BigInteger right) {
        final var product = left.multiply(right);
        return product.mod(P);
    }

    private static BigInteger plus(final BigInteger left, final BigInteger right) {
        final var sum = left.add(right);
        return sum.mod(P);
    }

    private static BigInteger minus(final BigInteger left, final BigInteger right) {
        final var difference = left.subtract(right);
        return difference.mod(P);
    }

    private static BigInteger field() {
        final var curve = SPEC.getCurve();
        final var field = (ECFieldFp) curve.getField();
        return field.getP();
    }

    private static void copyFixed(final BigInteger value, final byte[] out, final int offset) {
        final var raw = value.toByteArray();
        final var start = Math.max(0, raw.length - SCALAR_BYTES);
        final var length = raw.length - start;
        System.arraycopy(raw, start, out, offset + SCALAR_BYTES - length, length);
    }

    private static ECParameterSpec spec() {
        try {
            final var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        }
        catch (final GeneralSecurityException failure) {
            throw new IllegalStateException("P-256 is required", failure);
        }
    }
}
