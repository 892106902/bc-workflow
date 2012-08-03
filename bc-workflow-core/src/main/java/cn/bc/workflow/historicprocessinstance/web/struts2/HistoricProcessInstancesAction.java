package cn.bc.workflow.historicprocessinstance.web.struts2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import cn.bc.BCConstants;
import cn.bc.core.query.condition.Condition;
import cn.bc.core.query.condition.Direction;
import cn.bc.core.query.condition.impl.AndCondition;
import cn.bc.core.query.condition.impl.IsNotNullCondition;
import cn.bc.core.query.condition.impl.IsNullCondition;
import cn.bc.core.query.condition.impl.OrderCondition;
import cn.bc.core.util.DateUtils;
import cn.bc.db.jdbc.RowMapper;
import cn.bc.db.jdbc.SqlObject;
import cn.bc.identity.web.SystemContext;
import cn.bc.web.formater.CalendarFormater;
import cn.bc.web.formater.EntityStatusFormater;
import cn.bc.web.struts2.ViewAction;
import cn.bc.web.ui.html.grid.Column;
import cn.bc.web.ui.html.grid.HiddenColumn4MapKey;
import cn.bc.web.ui.html.grid.IdColumn4MapKey;
import cn.bc.web.ui.html.grid.TextColumn4MapKey;
import cn.bc.web.ui.html.page.PageOption;
import cn.bc.web.ui.html.toolbar.Toolbar;
import cn.bc.web.ui.html.toolbar.ToolbarButton;
import cn.bc.web.ui.json.Json;

/**
 * 流程监控视图Action
 * 
 * @author lbj
 * 
 */

@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Controller
public class HistoricProcessInstancesAction extends
		ViewAction<Map<String, Object>> {
	private static final long serialVersionUID = 1L;
	public String status = String.valueOf(BCConstants.STATUS_ENABLED);

	@Override
	public boolean isReadonly() {
		SystemContext context = (SystemContext) this.getContext();
		// 配置权限：、超级管理员
		return !context.hasAnyRole(getText("key.role.bc.admin"),
				getText("key.role.bc.workflow"));
	}

	@Override
	protected OrderCondition getGridOrderCondition() {
		return new OrderCondition("a.start_time_", Direction.Desc);
	}

	@Override
	protected SqlObject<Map<String, Object>> getSqlObject() {
		SqlObject<Map<String, Object>> sqlObject = new SqlObject<Map<String, Object>>();
		// 构建查询语句,where和order by不要包含在sql中(要统一放到condition中)
		StringBuffer sql = new StringBuffer();

		sql.append("select a.id_,b.name_ as category,a.start_time_,a.end_time_,a.duration_,a.proc_inst_id_");
		sql.append(",b.version_ as version,b.key_ as key,c.name");
		sql.append(",getProcessInstanceSubject(a.proc_inst_id_) as subject");
		sql.append(" from act_hi_procinst a");
		sql.append(" inner join act_re_procdef b on b.id_=a.proc_def_id_");
		sql.append(" left join bc_identity_actor c on c.code=a.start_user_id_");
		sqlObject.setSql(sql.toString());

		// 注入参数
		sqlObject.setArgs(null);

		// 数据映射器
		sqlObject.setRowMapper(new RowMapper<Map<String, Object>>() {
			public Map<String, Object> mapRow(Object[] rs, int rowNum) {
				Map<String, Object> map = new HashMap<String, Object>();
				int i = 0;
				map.put("id", rs[i++]);
				map.put("category", rs[i++]);
				map.put("start_time", rs[i++]);
				map.put("end_time", rs[i++]);
				map.put("duration", rs[i++]);
				if (map.get("end_time") != null) {
					map.put("status", BCConstants.STATUS_DISABLED);
				} else
					map.put("status", BCConstants.STATUS_ENABLED);

				// 格式化耗时
				if (map.get("duration") != null)
					map.put("frmDuration",
							DateUtils.getWasteTime(Long.parseLong(map.get(
									"duration").toString())));

				map.put("procinstid", rs[i++]);
				map.put("version", rs[i++]);
				map.put("key", rs[i++]);
				map.put("startName", rs[i++]);// 发起人
				map.put("subject", rs[i++]);
				return map;
			}
		});
		return sqlObject;
	}

	@Override
	protected List<Column> getGridColumns() {
		List<Column> columns = new ArrayList<Column>();
		columns.add(new IdColumn4MapKey("a.id_", "id"));
		// 状态
		columns.add(new TextColumn4MapKey("", "status",
				getText("flow.instance.status"), 50).setSortable(true)
				.setValueFormater(new EntityStatusFormater(getStatus())));
		// 主题
		columns.add(new TextColumn4MapKey(
				"getProcessInstanceSubject(a.proc_inst_id_)", "subject",
				getText("flow.instance.subject"), 200).setSortable(true)
				.setUseTitleFromLabel(true));
		// 流程
		columns.add(new TextColumn4MapKey("b.name_", "category",
				getText("flow.instance.name"), 200).setSortable(true)
				.setUseTitleFromLabel(true));
		// 版本号
		columns.add(new TextColumn4MapKey("b.version_", "version",
				getText("flow.instance.version"), 50).setSortable(true)
				.setUseTitleFromLabel(true));
		// 发起人
		columns.add(new TextColumn4MapKey("a.first_", "startName",
				getText("flow.instance.startName"), 80).setSortable(true)
				.setUseTitleFromLabel(true));
		columns.add(new TextColumn4MapKey("a.start_time_", "start_time",
				getText("flow.instance.startTime"), 150).setSortable(true)
				.setUseTitleFromLabel(true)
				.setValueFormater(new CalendarFormater("yyyy-MM-dd HH:mm:ss")));
		columns.add(new TextColumn4MapKey("a.end_time_", "end_time",
				getText("flow.instance.endTime"), 150).setSortable(true)
				.setUseTitleFromLabel(true)
				.setValueFormater(new CalendarFormater("yyyy-MM-dd HH:mm:ss")));
		columns.add(new TextColumn4MapKey("a.duration_", "frmDuration",
				getText("flow.instance.duration"), 80).setSortable(true));
		columns.add(new TextColumn4MapKey("b.key_", "key",
				getText("flow.instance.key"), 180).setSortable(true)
				.setUseTitleFromLabel(true));
		columns.add(new HiddenColumn4MapKey("procinstid", "procinstid"));
		return columns;
	}

	/**
	 * 状态值转换:流转中|已完成|全部
	 * 
	 */
	private Map<String, String> getStatus() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put(String.valueOf(BCConstants.STATUS_ENABLED),
				getText("flow.instance.status.processing"));
		map.put(String.valueOf(BCConstants.STATUS_DISABLED),
				getText("flow.instance.status.finished"));
		map.put("", getText("bc.status.all"));
		return map;
	}

	@Override
	protected String getGridRowLabelExpression() {
		return "['subject']";
	}

	@Override
	protected String[] getGridSearchFields() {
		return new String[] { "b.name_", "b.key_", "c.first_",
				"getProcessInstanceSubject(a.proc_inst_id_)" };
	}

	@Override
	protected String getFormActionName() {
		return "historicProcessInstance";
	}

	@Override
	protected PageOption getHtmlPageOption() {
		return super.getHtmlPageOption().setWidth(800).setMinWidth(400)
				.setHeight(400).setMinHeight(300);
	}

	@Override
	protected Toolbar getHtmlPageToolbar() {
		Toolbar tb = new Toolbar();
		// 发起流程
		tb.addButton(new ToolbarButton().setIcon("ui-icon-play")
				.setText(getText("flow.start"))
				.setClick("bc.historicProcessInstanceSelectView.startflow"));
		// 查看
		tb.addButton(new ToolbarButton().setIcon("ui-icon-check")
				.setText(getText("label.read"))
				.setClick("bc.historicProcessInstanceSelectView.open"));

		tb.addButton(Toolbar.getDefaultToolbarRadioGroup(this.getStatus(),
				"status", BCConstants.STATUS_ENABLED,
				getText("title.click2changeSearchStatus")));

		// 搜索按钮
		tb.addButton(this.getDefaultSearchToolbarButton());

		return tb;
	}

	@Override
	protected Condition getGridSpecalCondition() {
		// 状态条件
		AndCondition ac = new AndCondition();
		if (status != null && status.length() > 0) {
			String[] ss = status.split(",");
			if (ss.length == 1) {
				if (ss[0].equals(String.valueOf(BCConstants.STATUS_ENABLED))) {
					ac.add(new IsNullCondition("a.end_time_"));
				} else if (ss[0].equals(String
						.valueOf(BCConstants.STATUS_DISABLED)))
					ac.add(new IsNotNullCondition("a.end_time_"));
			}
		}
		return ac.isEmpty() ? null : ac;
	}

	@Override
	protected Json getGridExtrasData() {
		Json json = new Json();
		// 状态条件
		if (status != null && status.length() > 0)
			json.put("status", status);
		return json;
	}

	@Override
	protected String getGridDblRowMethod() {
		return "bc.historicProcessInstanceSelectView.open";
	}

	@Override
	protected String getHtmlPageJs() {
		return this.getHtmlPageNamespace()
				+ "/historicprocessinstance/select.js";
	}

	@Override
	protected String getHtmlPageNamespace() {
		return this.getContextPath() + "/bc-workflow";
	}

	// ==高级搜索代码开始==
	@Override
	protected boolean useAdvanceSearch() {
		return false;
	}

	@Override
	protected void initConditionsFrom() throws Exception {

	}
	// ==高级搜索代码结束==

}
