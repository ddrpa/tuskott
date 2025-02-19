package cc.ddrpa.tuskott.autoconfigure;

import cc.ddrpa.tuskott.event.EventCallback;
import cc.ddrpa.tuskott.event.OnUploadCreation;
import cc.ddrpa.tuskott.event.OnUploadSuccess;
import cc.ddrpa.tuskott.event.UploadCreateEvent;
import cc.ddrpa.tuskott.event.UploadSuccessEvent;
import cc.ddrpa.tuskott.tus.TuskottProcessor;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.LockManager;
import cc.ddrpa.tuskott.tus.provider.StoreProvider;
import cc.ddrpa.tuskott.tus.provider.impl.FileSystemStoreProvider;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
    @ConditionalOnMissingBean(TuskottProcessor.class)
    TuskottProcessor defaultTuskottProcessor() {
        StoreProvider storeProvider;
        String customizedStoreProvider = tuskottProperties.getStoreProvider();
        if (StringUtils.hasText(customizedStoreProvider)) {
            try {
                storeProvider = (StoreProvider) applicationContext.getBean(
                    Class.forName(customizedStoreProvider));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("storeProvider not found", e);
            }
        } else {
            storeProvider = new FileSystemStoreProvider();
        }
        FileInfoProvider fileInfoProvider;
        String customizedFileInfoProvider = tuskottProperties.getFileInfoProvider();
        if (StringUtils.hasText(customizedFileInfoProvider)) {
            try {
                fileInfoProvider = (FileInfoProvider) applicationContext.getBean(
                    Class.forName(customizedFileInfoProvider));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("fileInfoProvider not found", e);
            }
        } else {
            fileInfoProvider = new InMemoryFileInfoProvider();
        }
        LockManager lockManager;
        String customizedLockManager = tuskottProperties.getLockManager();
        if (StringUtils.hasText(customizedLockManager)) {
            try {
                lockManager = (LockManager) applicationContext.getBean(
                    Class.forName(customizedLockManager));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("lockManager not found", e);
            }
        } else {
            lockManager = new InMemoryLockManager();
        }
        return new TuskottProcessor(tuskottProperties, fileInfoProvider, storeProvider,
            lockManager);
    }

    @Bean
    public ApplicationRunner runner(RequestMappingHandlerMapping handlerMapping,
        TuskottProcessor tuskottProcessor) {
        return args -> {
            registerEndpoints(handlerMapping, tuskottProcessor);
            registerEventHandler(tuskottProcessor);
        };
    }

    private void registerEndpoints(
        RequestMappingHandlerMapping handlerMapping,
        TuskottProcessor tuskottProcessor) throws NoSuchMethodException {
        String endpoint = tuskottProperties.getEndpoint();

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files")
                .methods(RequestMethod.OPTIONS)
                .build(),
            tuskottProcessor,
            new HandlerMethod(tuskottProcessor, "options", HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files")
                .methods(RequestMethod.POST)
                .build(),
            tuskottProcessor,
            new HandlerMethod(tuskottProcessor, "creation", HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files/{fileInfoID}")
                .methods(RequestMethod.HEAD)
                .build(),
            tuskottProcessor,
            new HandlerMethod(tuskottProcessor, "head", String.class, HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files/{fileInfoID}")
                .methods(RequestMethod.PATCH)
                .build(),
            tuskottProcessor,
            new HandlerMethod(tuskottProcessor, "patch", String.class, HttpServletRequest.class,
                HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
            RequestMappingInfo
                .paths(endpoint + "/files/{fileInfoID}")
                .methods(RequestMethod.DELETE)
                .build(),
            tuskottProcessor,
            new HandlerMethod(tuskottProcessor, "termination", String.class,
                HttpServletRequest.class,
                HttpServletResponse.class).getMethod());
    }

    private void registerEventHandler(TuskottProcessor tuskottProcessor) {
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
        tuskottProcessor.registerCallBack(onSuccessCallback, onCreateCallback);
    }
}
