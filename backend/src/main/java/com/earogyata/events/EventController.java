package com.earogyata.events;
import com.earogyata.auth.AppPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;
@RestController
public class EventController {
 private final EventHub hub;
 public EventController(EventHub hub) { this.hub=hub; }
 @GetMapping(value="/api/v1/events",produces="text/event-stream") public SseEmitter events(Authentication auth,HttpServletResponse res) {
  res.setHeader("Cache-Control","no-cache, no-store");res.setHeader("X-Accel-Buffering","no");return hub.connect(AppPrincipal.of(auth));
 }
}
