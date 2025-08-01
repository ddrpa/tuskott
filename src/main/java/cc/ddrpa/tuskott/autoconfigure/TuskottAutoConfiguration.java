package cc.ddrpa.tuskott.autoconfigure;

import cc.ddrpa.tuskott.event.PostCompleteEvent;
import cc.ddrpa.tuskott.event.PostCreateEvent;
import cc.ddrpa.tuskott.event.PostTerminateEvent;
import cc.ddrpa.tuskott.event.TuskottEventCallback;
import cc.ddrpa.tuskott.event.annotation.PostComplete;
import cc.ddrpa.tuskott.event.annotation.PostCreate;
import cc.ddrpa.tuskott.event.annotation.PostTerminate;
import cc.ddrpa.tuskott.properties.TuskottProperties;
import cc.ddrpa.tuskott.tus.TuskottProcessor;
import cc.ddrpa.tuskott.tus.lock.LockProvider;
import cc.ddrpa.tuskott.tus.resource.UploadResourceTracker;
import cc.ddrpa.tuskott.tus.storage.Storage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    TuskottProcessor tuskottProcessor() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<Storage> storageClass = (Class<Storage>) Class.forName(tuskottProperties.getStorage().getProvider());
        Map<String, Object> storageConfig = tuskottProperties.getStorage().getConfig();
        Storage storage = storageClass.getDeclaredConstructor(Map.class).newInstance(storageConfig);

        Class<UploadResourceTracker> uploadResourceHolderClass = (Class<UploadResourceTracker>) Class.forName(tuskottProperties.getTracker().getProvider());
        Map<String, Object> trackerConfig = tuskottProperties.getTracker().getConfig();
        UploadResourceTracker uploadResourceTracker = uploadResourceHolderClass.getDeclaredConstructor(Map.class).newInstance(trackerConfig);

        Class<LockProvider> lockProviderClass = (Class<LockProvider>) Class.forName(tuskottProperties.getLock().getProvider());
        Map<String, Object> lockConfig = tuskottProperties.getLock().getConfig();
        LockProvider lockProvider = lockProviderClass.getDeclaredConstructor(Map.class).newInstance(lockConfig);

        return new TuskottProcessor(tuskottProperties, uploadResourceTracker, storage, lockProvider);
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
        String baseEndpoint = tuskottProperties.getBasePath() + "/files";
        TuskottProperties.Extension extensionConfiguration = tuskottProperties.getExtension();

        handlerMapping.registerMapping(
                RequestMappingInfo
                        .paths(baseEndpoint)
                        .methods(RequestMethod.OPTIONS)
                        .build(),
                tuskottProcessor,
                new HandlerMethod(tuskottProcessor, "options", HttpServletRequest.class,
                        HttpServletResponse.class).getMethod());

        if (extensionConfiguration.isEnableCreation()) {
            handlerMapping.registerMapping(
                    RequestMappingInfo
                            .paths(baseEndpoint)
                            .methods(RequestMethod.POST)
                            .build(),
                    tuskottProcessor,
                    new HandlerMethod(tuskottProcessor, "create", HttpServletRequest.class,
                            HttpServletResponse.class).getMethod());
        }

        handlerMapping.registerMapping(
                RequestMappingInfo
                        .paths(baseEndpoint + "/{resource}")
                        .methods(RequestMethod.HEAD)
                        .build(),
                tuskottProcessor,
                new HandlerMethod(tuskottProcessor, "head", String.class, HttpServletRequest.class,
                        HttpServletResponse.class).getMethod());

        handlerMapping.registerMapping(
                RequestMappingInfo
                        .paths(baseEndpoint + "/{resource}")
                        .methods(RequestMethod.PATCH)
                        .build(),
                tuskottProcessor,
                new HandlerMethod(tuskottProcessor, "patch", String.class, HttpServletRequest.class,
                        HttpServletResponse.class).getMethod());

        if (extensionConfiguration.isEnableTermination()) {
            handlerMapping.registerMapping(
                    RequestMappingInfo
                            .paths(baseEndpoint + "/{resource}")
                            .methods(RequestMethod.DELETE)
                            .build(),
                    tuskottProcessor,
                    new HandlerMethod(tuskottProcessor, "termination", String.class,
                            HttpServletRequest.class,
                            HttpServletResponse.class).getMethod());
        }
    }

    private void registerEventHandler(TuskottProcessor tuskottProcessor) {
        List<TuskottEventCallback> postCreateCallback = new ArrayList<>();
        List<TuskottEventCallback> postFinishCallback = new ArrayList<>();
        List<TuskottEventCallback> postTerminateCallback = new ArrayList<>();
        // 扫描 @Component 里的 Bean
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Component.class);
        for (Object bean : beans.values()) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostComplete.class)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == PostCompleteEvent.class) {
                    postFinishCallback.add(new TuskottEventCallback(bean, method));
                } else if (method.isAnnotationPresent(PostCreate.class)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == PostCreateEvent.class) {
                    postCreateCallback.add(new TuskottEventCallback(bean, method));
                } else if (method.isAnnotationPresent(PostTerminate.class)
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == PostTerminateEvent.class) {
                    postTerminateCallback.add(new TuskottEventCallback(bean, method));
                }
            }
        }
        tuskottProcessor.registerCallBack(postCreateCallback, postFinishCallback,
                postTerminateCallback);
    }
}
