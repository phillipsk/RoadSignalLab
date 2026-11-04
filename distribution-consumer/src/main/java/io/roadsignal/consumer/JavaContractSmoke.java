package io.roadsignal.consumer;
import android.content.Context;
import io.roadsignal.sdk.JavaRoadSignalClient;
import io.roadsignal.sdk.RoadSignal;
import java.io.Closeable;
import java.io.IOException;
/** Compiles a Java host against the actual public SDK without Kotlin Continuation boilerplate. */
public final class JavaContractSmoke {
    public static String describe(Context context) {
        try (JavaRoadSignalClient client = new JavaRoadSignalClient(context)) {
            Closeable observation = client.observe(snapshot -> { /* bind to a Java host owner */ });
            observation.close();
            return "Java callback/observer API compiled: " + RoadSignal.VERSION;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
