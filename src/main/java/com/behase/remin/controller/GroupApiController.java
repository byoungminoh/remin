package com.behase.remin.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.behase.remin.exception.InvalidParameterException;
import com.behase.remin.model.Group;
import com.behase.remin.model.Notice;
import com.behase.remin.service.GroupService;
import com.behase.remin.service.LoggingOperationService;
import com.behase.remin.service.NodeService;
import com.behase.remin.util.JedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api")
public class GroupApiController {
	@Autowired
	GroupService groupService;

	@Autowired
	NodeService nodeService;

	@Autowired
	private LoggingOperationService loggingOperationService;

	@Autowired
	ObjectMapper mapper;

	@RequestMapping(value = "/groups", method = RequestMethod.GET)
	public Object getGroupList(
			@RequestParam(defaultValue = "") String full
			) {
		Set<String> groupNamesSet = groupService.getGroups();
		List<String> groupNames = Lists.newArrayList(groupNamesSet);
		Collections.sort(groupNames);

		if (StringUtils.equalsIgnoreCase(full, "true")) {
			List<Group> groups = Lists.newArrayList();
			groupNames.forEach(groupName -> {
				try {
					groups.add(groupService.getGroup(groupName));
				} catch (Exception e) {
					log.error("Failed to get group. groupName = {}", groupName, e);
				}
			});
			return groups;
		} else {
			return groupNames;
		}
	}

	@RequestMapping(value = "/group/{groupName}", method = RequestMethod.GET)
	public Group getGroup(
			@PathVariable String groupName
			) throws IOException {
		return groupService.getGroup(groupName);
	}

	@RequestMapping(value = "/group/{groupName}", method = RequestMethod.POST)
	public Group setGroup(
			Authentication authentication,
			@PathVariable String groupName,
			@RequestParam String hostAndPorts
			) throws IOException {
		loggingOperationService.log("updateGroup", authentication, "groupName={}, hostAndPorts={}.", groupName, hostAndPorts);

		if (groupService.existsGroupName(groupName)) {
			throw new InvalidParameterException(String.format("This groupName(%s) already exists.", groupName));
		}
		groupService.setGroup(groupName, Lists.newArrayList(JedisUtils.getHostAndPorts(Splitter.on(",").trimResults().splitToList(hostAndPorts))));
		return groupService.getGroup(groupName);
	}

	@RequestMapping(value = "/group/{groupName}/delete", method = RequestMethod.POST)
	public Map<String, Boolean> deleteGroupByPost(
			Authentication authentication,
			@PathVariable String groupName
			) {
		loggingOperationService.log("deleteGroup", authentication, "groupName={}.", groupName);

		groupService.deleteGroup(groupName);

		Map<String, Boolean> result = Maps.newHashMap();
		result.put("isSuccess", true);
		return result;
	}

	@RequestMapping(value = "/group/{groupName}/metrics", method = {RequestMethod.GET, RequestMethod.POST})
	public Object getMetrics(
			@PathVariable String groupName,
			@RequestParam(defaultValue = "") String nodes,
			@RequestParam(defaultValue = "") String fields,
			@RequestParam(defaultValue = "") String start,
			@RequestParam(defaultValue = "") String end
			) {
		long startLong;
		long endLong;
		try {
			startLong = Long.valueOf(start);
		} catch (Exception e) {
			throw new InvalidParameterException("'start' is must be number.");
		}
		try {
			endLong = Long.valueOf(end);
		} catch (Exception e) {
			throw new InvalidParameterException("'end' is must be number.");
		}

		List<String> nodesList = Lists.newArrayList();
		if (StringUtils.isNotBlank(nodes)) {
			nodesList.addAll(Splitter.on(",").splitToList(nodes));
		}
		if (nodesList.isEmpty()) {
			throw new InvalidParameterException("'nodes' is empty.");
		}

		List<String> fieldsList = Lists.newArrayList();
		if (StringUtils.isNotBlank(fields)) {
			fieldsList.addAll(Splitter.on(",").splitToList(fields));
		}
		if (fieldsList.isEmpty()) {
			throw new InvalidParameterException("'fields' is empty.");
		}

		return groupService.getGroupStaticsInfoHistory(groupName, nodesList, fieldsList, startLong, endLong);
	}

	@RequestMapping(value = "/group/{groupName}/notice", method = RequestMethod.GET)
	public Object getGroupNotice(
			@PathVariable String groupName
			) throws IOException {
		Notice notice = groupService.getGroupNotice(groupName);
		if (notice == null) {
			return new Notice();
		}
		return notice;
	}

	@RequestMapping(value = "/group/{groupName}/notice", method = RequestMethod.POST)
	public Object setGroupNotice(
			Authentication authentication,
			@PathVariable String groupName,
			@RequestParam(defaultValue = "") String notice
			) throws IOException {
		loggingOperationService.log("addNotice", authentication, "groupName={}, notice={}.", groupName, notice);

		if (StringUtils.isBlank(notice)) {
			throw new InvalidParameterException("'notice' is blank.");
		}

		Notice noticeObj;
		try {
			noticeObj = mapper.readValue(notice, Notice.class);
		} catch (Exception e) {
			throw new InvalidParameterException("'notice' is invalid format.");
		}

		groupService.setGroupNotice(groupName, noticeObj);
		return groupService.getGroupNotice(groupName);
	}
}