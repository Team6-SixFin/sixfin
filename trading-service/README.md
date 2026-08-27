docs/intellij/sixfin-live-templates.zip을
IntelliJ → File → Manage IDE Settings → Import Settings에서 Import 해주세요.
이후 doc를 친후 탭키를 누르면
/**
* 작성자 :
* 최초 작성일 :
* 최종 수정일 :
* 기능 :
* 설명 :
* @Param:
  **/

이런 템플릿을 사용할 수 있습니다.

구조
{service}/
├── presentation/    HTTP 요청/응답 (Controller, Request/Response DTO)
├── application/     유스케이스 조합 (Facade, CommandService/QueryService, Command/Query DTO, Event, Port)
├── domain/          순수 비즈니스 로직 (Entity, Repository 인터페이스)
├── infrastructure/  외부 연동 구현체 (JPA Repository 구현체, Feign, Messaging, Config)
└── global/          이 서비스만의 공통 응답/예외/BaseEntity
