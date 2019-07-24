# Yarn状态机抽取demo-LocalizedResource的状态机

<a name="yfJe4"></a>
## 通用状态机的作用

通用状态机，可以用java代码描述出状态机的状态、事件、事件处理函数，就可以构造一个状态机。

使用状态机事，只需要向状态机投递事件event即可。这样简化了实现业务逻辑时的过程，不需要关注复杂状态机内部的行为，只需要关注自己发生了什么事件即可。


<a name="VtUTY"></a>
## 状态机的show


通过这个项目运行,可以输出Graphviz格式文件。


```
digraph aaa {
graph [ label="aaa", fontsize=24, fontname=Helvetica];
node [fontsize=12, fontname=Helvetica];
edge [fontsize=9, fontcolor=blue, fontname=Arial];
"aaa.INIT" [ label = INIT ];
"aaa.INIT" -> "aaa.LOCALIZED" [ label = "RECOVERED" ];
"aaa.INIT" -> "aaa.DOWNLOADING" [ label = "REQUEST" ];
"aaa.DOWNLOADING" [ label = DOWNLOADING ];
"aaa.DOWNLOADING" -> "aaa.DOWNLOADING" [ label = "RELEASE,\nREQUEST" ];
"aaa.DOWNLOADING" -> "aaa.FAILED" [ label = "LOCALIZATION_FAILED" ];
"aaa.DOWNLOADING" -> "aaa.LOCALIZED" [ label = "LOCALIZED" ];
"aaa.LOCALIZED" [ label = LOCALIZED ];
"aaa.LOCALIZED" -> "aaa.LOCALIZED" [ label = "RELEASE,\nREQUEST" ];
"aaa.FAILED" [ label = FAILED ];
}

```


![](https://intranetproxy.alipay.com/skylark/lark/__graphviz/a4693f5164eb9fed466932c29fbaf39d.svg#lake_card_v2=eyJjb2RlIjoiZGlncmFwaCBhYWEge1xuZ3JhcGggWyBsYWJlbD1cImFhYVwiLCBmb250c2l6ZT0yNCwgZm9udG5hbWU9SGVsdmV0aWNhXTtcbm5vZGUgW2ZvbnRzaXplPTEyLCBmb250bmFtZT1IZWx2ZXRpY2FdO1xuZWRnZSBbZm9udHNpemU9OSwgZm9udGNvbG9yPWJsdWUsIGZvbnRuYW1lPUFyaWFsXTtcblwiYWFhLklOSVRcIiBbIGxhYmVsID0gSU5JVCBdO1xuXCJhYWEuSU5JVFwiIC0-IFwiYWFhLkxPQ0FMSVpFRFwiIFsgbGFiZWwgPSBcIlJFQ09WRVJFRFwiIF07XG5cImFhYS5JTklUXCIgLT4gXCJhYWEuRE9XTkxPQURJTkdcIiBbIGxhYmVsID0gXCJSRVFVRVNUXCIgXTtcblwiYWFhLkRPV05MT0FESU5HXCIgWyBsYWJlbCA9IERPV05MT0FESU5HIF07XG5cImFhYS5ET1dOTE9BRElOR1wiIC0-IFwiYWFhLkRPV05MT0FESU5HXCIgWyBsYWJlbCA9IFwiUkVMRUFTRSxcXG5SRVFVRVNUXCIgXTtcblwiYWFhLkRPV05MT0FESU5HXCIgLT4gXCJhYWEuRkFJTEVEXCIgWyBsYWJlbCA9IFwiTE9DQUxJWkFUSU9OX0ZBSUxFRFwiIF07XG5cImFhYS5ET1dOTE9BRElOR1wiIC0-IFwiYWFhLkxPQ0FMSVpFRFwiIFsgbGFiZWwgPSBcIkxPQ0FMSVpFRFwiIF07XG5cImFhYS5MT0NBTElaRURcIiBbIGxhYmVsID0gTE9DQUxJWkVEIF07XG5cImFhYS5MT0NBTElaRURcIiAtPiBcImFhYS5MT0NBTElaRURcIiBbIGxhYmVsID0gXCJSRUxFQVNFLFxcblJFUVVFU1RcIiBdO1xuXCJhYWEuRkFJTEVEXCIgWyBsYWJlbCA9IEZBSUxFRCBdO1xufVxuIiwidXJsIjoiaHR0cHM6Ly9pbnRyYW5ldHByb3h5LmFsaXBheS5jb20vc2t5bGFyay9sYXJrL19fZ3JhcGh2aXovYTQ2OTNmNTE2NGViOWZlZDQ2NjkzMmMyOWZiYWYzOWQuc3ZnIiwidHlwZSI6ImdyYXBodml6IiwiaWQiOiJqVVpTbyIsImNhcmQiOiJkaWFncmFtIn0=)
核心是如下构造状态机过程

```java
 private static final StateMachineFactory<LocalizedResource,ResourceState,
        ResourceEventType,ResourceEvent> stateMachineFactory =
        new StateMachineFactory<LocalizedResource,ResourceState,
          ResourceEventType,ResourceEvent>(ResourceState.INIT)

    // From INIT (ref == 0, awaiting req)
    .addTransition(ResourceState.INIT, ResourceState.DOWNLOADING,
        ResourceEventType.REQUEST, new FetchResourceTransition())
    .addTransition(ResourceState.INIT, ResourceState.LOCALIZED,
        ResourceEventType.RECOVERED, new RecoveredTransition())

    // From DOWNLOADING (ref > 0, may be localizing)
    .addTransition(ResourceState.DOWNLOADING, ResourceState.DOWNLOADING,
        ResourceEventType.REQUEST, new FetchResourceTransition()) // TODO: Duplicate addition!!
    .addTransition(ResourceState.DOWNLOADING, ResourceState.LOCALIZED,
        ResourceEventType.LOCALIZED, new FetchSuccessTransition())
    .addTransition(ResourceState.DOWNLOADING,ResourceState.DOWNLOADING,
        ResourceEventType.RELEASE, new ReleaseTransition())
    .addTransition(ResourceState.DOWNLOADING, ResourceState.FAILED,
        ResourceEventType.LOCALIZATION_FAILED, new FetchFailedTransition())

    // From LOCALIZED (ref >= 0, on disk)
    .addTransition(ResourceState.LOCALIZED, ResourceState.LOCALIZED,
        ResourceEventType.REQUEST, new LocalizedResourceTransition())
    .addTransition(ResourceState.LOCALIZED, ResourceState.LOCALIZED,
        ResourceEventType.RELEASE, new ReleaseTransition())
    .installTopology();
```

