package com.szr.crosserp.packing.controller;

import com.szr.crosserp.packing.dto.PackingDtos.*;
import com.szr.crosserp.packing.service.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/packing")
public class PackingController {
    private final PackingQueryService query;private final PackingCommandService commands;
    public PackingController(PackingQueryService query,PackingCommandService commands){this.query=query;this.commands=commands;}
    @GetMapping("/tasks") TaskPage tasks(@RequestParam(required=false)String keyword,@RequestParam(required=false)String status){return query.findTasks(keyword,status);}
    @GetMapping("/orders/{orderId}") Detail detail(@PathVariable Long orderId){return query.findDetail(orderId);}
    @PostMapping("/orders/{orderId}/start") CommandResponse start(@PathVariable Long orderId,@RequestBody(required=false)StartRequest request,@AuthenticationPrincipal Jwt jwt){return commands.start(orderId,request,jwt.getSubject(),roles(jwt));}
    @PostMapping("/tasks/{taskId}/boxes") CommandResponse addBox(@PathVariable Long taskId,@RequestBody SaveBoxRequest request,@AuthenticationPrincipal Jwt jwt){return commands.addBox(taskId,request,jwt.getSubject(),roles(jwt));}
    @PutMapping("/tasks/{taskId}/boxes/{boxId}") CommandResponse updateBox(@PathVariable Long taskId,@PathVariable Long boxId,@RequestBody SaveBoxRequest request,@AuthenticationPrincipal Jwt jwt){return commands.updateBox(taskId,boxId,request,jwt.getSubject(),roles(jwt));}
    @DeleteMapping("/tasks/{taskId}/boxes/{boxId}") CommandResponse deleteBox(@PathVariable Long taskId,@PathVariable Long boxId,@RequestParam Long version,@AuthenticationPrincipal Jwt jwt){return commands.deleteBox(taskId,boxId,version,jwt.getSubject(),roles(jwt));}
    @PostMapping("/tasks/{taskId}/complete") CommandResponse complete(@PathVariable Long taskId,@RequestBody CompleteRequest request,@AuthenticationPrincipal Jwt jwt){return commands.complete(taskId,request,jwt.getSubject(),roles(jwt));}
    @PostMapping("/tasks/{taskId}/cancel") CommandResponse cancel(@PathVariable Long taskId,@RequestBody CancelRequest request,@AuthenticationPrincipal Jwt jwt){return commands.cancel(taskId,request,jwt.getSubject(),roles(jwt));}
    private List<String> roles(Jwt jwt){List<String> values=jwt.getClaimAsStringList("roles");return values==null?List.of():values;}
}
