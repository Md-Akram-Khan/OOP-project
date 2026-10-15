package electricity.billing.system.ui.components;

import electricity.billing.system.common.AppException;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Runs server calls off the Swing thread so the window never freezes. */
public final class Tasks {

    private Tasks() {
    }

    /** Runs the task in the background; failures appear as an error toast on the owner's window. */
    public static <T> void run(Component owner, Callable<T> task, Consumer<T> onSuccess) {
        run(task, onSuccess, error -> Toast.error(owner, message(error)));
    }

    public static <T> void run(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    onError.accept(e.getCause());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }

    /** A message fit for the user. Unexpected errors are also printed for debugging. */
    public static String message(Throwable error) {
        if (error instanceof AppException) {
            return error.getMessage();
        }
        error.printStackTrace();
        return "Something went wrong: " + error.getMessage();
    }
}
