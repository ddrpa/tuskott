package cc.ddrpa.tuskott.autoconfigure;

import cc.ddrpa.tuskott.event.EventCallback;
import cc.ddrpa.tuskott.event.OnUploadCreation;
import cc.ddrpa.tuskott.event.OnUploadSuccess;
import cc.ddrpa.tuskott.event.UploadCreateEvent;
import cc.ddrpa.tuskott.event.UploadSuccessEvent;
import cc.ddrpa.tuskott.tus.RequestHandler;
import cc.ddrpa.tuskott.tus.provider.BlobStoreProvider;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.LockManager;
import cc.ddrpa.tuskott.tus.provider.impl.FileSystemBlobStoreProvider;
import cc.ddrpa.tuskott.tus.provider.impl.InMemoryFileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.impl.InMemoryLockManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@EnableConfigurationProperties(TuskottProperties.class)
public class TuskottAutoConfiguration implements ApplicationContextAware {

    private final TuskottProperties tuskottProperties;
    private ApplicationContext applicationContext;

    public TuskottAutoConfiguration(TuskottProperties tuskottProperties) {
        this.tuskottProperties = tuskottProperties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnMissingClass("LockManager")
    LockManager lockManager() {
        return new InMemoryLockManager();
    }

    @Bean
    @ConditionalOnMissingClass("FileInfoProvider")
    FileInfoProvider fileInfoProvider() {
        return new InMemoryFileInfoProvider();
    }

    @Bean
    @ConditionalOnMissingClass("BlobStoreProvider")
    BlobStoreProvider blobStoreProvider() {
        return new FileSystemBlobStoreProvider();
    }

    @Bean
    @DependsOn({"fileInfoProvider", "blobStoreProvider", "lockManager", "tusEventHandler"})
    RequestHandler requestHandler(FileInfoProvider fileInfoProvider,
        BlobStoreProvider blobStoreProvider, LockManager lockManager) {
        return new RequestHandler(tuskottProperties, fileInfoProvider, blobStoreProvider,
            lockManager);
    }

    @Bean
    public ApplicationRunner runner(RequestMappingHandlerMapping handlerMapping,
        RequestHandler requestHandler) {
        return args -> {
            registerEndpoints(handlerMapping, requestHandler);
            registerEventHandler(requestHandler);
        };
    }

    private void registerEndpoints(
        RequestMappingHandlerMapping handlerMapping,
        RequestHandler requestHandler) throws NoSuchMethodException {
        String endpoint = tuskottProperties.getEndpoint();

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files")
                .methods(RequestMethod.OPTIONS)
                .build(),
            requestHandler,
            new HandlerMethod(requestHandler, "options", HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files")
                .methods(RequestMethod.POST)
                .build(),
            requestHandler,
            new HandlerMethod(requestHandler, "creation", HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files/{fileInfoID}")
                .methods(RequestMethod.HEAD)
                .build(),
            requestHandler,
            new HandlerMethod(requestHandler, "head", String.class, HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files/{fileInfoID}")
                .methods(RequestMethod.PATCH)
                .build(),
            requestHandler,
            new HandlerMethod(requestHandler, "patch", String.class, HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files/{fileInfoID}")
                .methods(RequestMethod.DELETE)
                .build(),
            requestHandler,
            new HandlerMethod(requestHandler, "termination", String.class,
                HttpServletRequest.class,
                HttpServletResponse.class).getMethod());
    }

    private void registerEventHandler(RequestHandler requestHandler) {
        List<EventCallback> onSuccessCallback = new ArrayList<>();
        List<EventCallback> onCreateCallback = new ArrayList<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(
            Component.class); // 扫描 @Component 里的 Bean
        for (Object bean : beans.values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(OnUploadSuccess.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == UploadSuccessEvent.class) {
                    onSuccessCallback.add(new EventCallback(bean, method));
                } else if (method.isAnnotationPresent(OnUploadCreation.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == UploadCreateEvent.class) {
                    onCreateCallback.add(new EventCallback(bean, method));
                }
            }
        }
        requestHandler.registerCallBack(onSuccessCallback, onCreateCallback);
    }
}
