package cc.ddrpa.tuskott.autoconfigure;

import cc.ddrpa.tuskott.hook.EventCallback;
import cc.ddrpa.tuskott.hook.PostCreateEvent;
import cc.ddrpa.tuskott.hook.PostFinishEvent;
import cc.ddrpa.tuskott.hook.PostTerminateEvent;
import cc.ddrpa.tuskott.hook.annotation.PostCreate;
import cc.ddrpa.tuskott.hook.annotation.PostFinish;
import cc.ddrpa.tuskott.hook.annotation.PostTerminate;
import cc.ddrpa.tuskott.properties.TuskottProperties;
import cc.ddrpa.tuskott.tus.TuskottProcessor;
import cc.ddrpa.tuskott.tus.provider.FileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.LockProvider;
import cc.ddrpa.tuskott.tus.provider.StorageBackend;
import cc.ddrpa.tuskott.tus.provider.impl.LocalDiskStorageBackend;
import cc.ddrpa.tuskott.tus.provider.impl.MemoryFileInfoProvider;
import cc.ddrpa.tuskott.tus.provider.impl.MemoryLocker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
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
    TuskottProcessor defaultTuskottProcessor() throws ClassNotFoundException {
        // TODO bean 初始化方式需要处理
        StorageBackend storageBackend;
        if (StringUtils.hasText(tuskottProperties.getStorageBackend().getStoreBackendProvider())) {
            Class<StorageBackend> storageBackendClass = (Class<StorageBackend>) Class.forName(
                tuskottProperties.getStorageBackend().getStoreBackendProvider());
            storageBackend = applicationContext.getBean(storageBackendClass);
        } else {
            storageBackend = new LocalDiskStorageBackend();
        }
        FileInfoProvider fileInfoProvider;
        if (StringUtils.hasText(tuskottProperties.getFileInfoProvider())) {
            Class<FileInfoProvider> fileInfoProviderClass = (Class<FileInfoProvider>) Class.forName(
                tuskottProperties.getFileInfoProvider());
            fileInfoProvider = applicationContext.getBean(fileInfoProviderClass);
        } else {
            fileInfoProvider = new MemoryFileInfoProvider();
        }
        LockProvider lockProvider;
        if (StringUtils.hasText(tuskottProperties.getLockProvider())) {
            Class<LockProvider> lockProviderClass = (Class<LockProvider>) Class.forName(
                tuskottProperties.getLockProvider());
            lockProvider = applicationContext.getBean(lockProviderClass);
        } else {
            lockProvider = new MemoryLocker();
        }
        return new TuskottProcessor(tuskottProperties, fileInfoProvider, storageBackend,
            lockProvider);
    }

    @Bean
    public ApplicationRunner runner(
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
        TuskottProcessor tuskottProcessor) {
        return args -> {
            registerEndpoints(handlerMapping, tuskottProcessor);
            registerEventHandler(tuskottProcessor);
        };
    }

    private void registerEndpoints(
        RequestMappingHandlerMapping handlerMapping,
        TuskottProcessor tuskottProcessor) throws NoSuchMethodException {
        String endpoint = tuskottProperties.getBasePath();

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
            new HandlerMethod(tuskottProcessor, "create", HttpServletRequest.class,
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
        List<EventCallback> postCreateCallback = new ArrayList<>();
        List<EventCallback> postFinishCallback = new ArrayList<>();
        List<EventCallback> postTerminateCallback = new ArrayList<>();
        // 扫描 @Component 里的 Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);
        for (Object bean : beans.values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostFinish.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == PostFinishEvent.class) {
                    postFinishCallback.add(new EventCallback(bean, method));
                } else if (method.isAnnotationPresent(PostCreate.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == PostCreateEvent.class) {
                    postCreateCallback.add(new EventCallback(bean, method));
                } else if (method.isAnnotationPresent(PostTerminate.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == PostTerminateEvent.class) {
                    postTerminateCallback.add(new EventCallback(bean, method));
                }
            }
        }
        tuskottProcessor.registerCallBack(postCreateCallback, postFinishCallback,
            postTerminateCallback);
    }
}
