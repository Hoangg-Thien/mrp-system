package mrp.integration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

public class QueryCountHelper {

    private static final AtomicInteger count = new AtomicInteger(0);

    public static void reset() {
        count.set(0);
    }

    public static int getCount() {
        return count.get();
    }

    public static DataSource wrap(DataSource target) {
        return (DataSource) Proxy.newProxyInstance(
                QueryCountHelper.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        Object result = method.invoke(target, args);
                        if (result instanceof Connection conn) {
                            return wrapConnection(conn);
                        }
                        return result;
                    }
                }
        );
    }

    private static Connection wrapConnection(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                QueryCountHelper.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        if ("prepareStatement".equals(name) || "createStatement".equals(name) || "prepareCall".equals(name)) {
                            count.incrementAndGet();
                        }
                        return method.invoke(target, args);
                    }
                }
        );
    }
}
