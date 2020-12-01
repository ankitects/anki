package test_engine;

import junit.framework.AssertionFailedError;
import org.unitils.reflectionassert.ReflectionAssert;
import org.unitils.reflectionassert.ReflectionComparatorMode;

public class Verifier {
    public static boolean verify(Object result, Object expected) {
        try {
            ReflectionAssert.assertReflectionEquals(result, expected, ReflectionComparatorMode.LENIENT_ORDER);
            return true;
        } catch (AssertionFailedError err) {
            return false;
        }
    }
}
