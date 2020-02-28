/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.room.poll;

import static org.apache.openmeetings.core.util.WebSocketHelper.sendRoom;
import static org.apache.openmeetings.web.app.WebSession.getUserId;
import static org.apache.openmeetings.web.common.confirmation.ConfirmableAjaxBorder.newOkCancelDangerConfirm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.openmeetings.db.dao.room.PollDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.room.RoomPoll;
import org.apache.openmeetings.db.entity.room.RoomPollAnswer;
import org.apache.openmeetings.db.util.ws.RoomMessage;
import org.apache.openmeetings.web.common.MainPanel;
import org.apache.openmeetings.web.common.OmModalCloseButton;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.wicketstuff.jqplot.behavior.JqPlotBehavior;
import org.wicketstuff.jqplot.behavior.JqPlotCssResourceReference;
import org.wicketstuff.jqplot.behavior.JqPlotJavascriptResourceReference;
import org.wicketstuff.jqplot.lib.Chart;
import org.wicketstuff.jqplot.lib.ChartConfiguration;
import org.wicketstuff.jqplot.lib.JqPlotResources;
import org.wicketstuff.jqplot.lib.JqPlotUtils;
import org.wicketstuff.jqplot.lib.chart.BarChart;
import org.wicketstuff.jqplot.lib.chart.PieChart;
import org.wicketstuff.jqplot.lib.elements.Highlighter;
import org.wicketstuff.jqplot.lib.elements.Location;
import org.wicketstuff.jqplot.lib.elements.RendererOptions;

import de.agilecoders.wicket.core.markup.html.bootstrap.button.BootstrapAjaxLink;
import de.agilecoders.wicket.core.markup.html.bootstrap.button.Buttons;
import de.agilecoders.wicket.core.markup.html.bootstrap.dialog.Modal;
import de.agilecoders.wicket.extensions.markup.html.bootstrap.icon.FontAwesome5IconType;

/**
 * @author solomax
 *
 */
public class PollResultsDialog extends Modal<RoomPoll> {
	private static final long serialVersionUID = 1L;
	private final WebMarkupContainer chartDiv = new WebMarkupContainer("chart");
	private final Long roomId;
	private PollSelectForm selForm;
	private PollResultsForm dispForm;
	private BootstrapAjaxLink<String> close;
	private BootstrapAjaxLink<String> delete;
	private BootstrapAjaxLink<String> clone;
	private boolean moderator = false;
	private boolean opened = false;
	private final CreatePollDialog createPoll;
	@SpringBean
	private PollDao pollDao;
	@SpringBean
	private UserDao userDao;

	public PollResultsDialog(String id, CreatePollDialog createPoll, Long _roomId) {
		super(id);
		this.roomId = _roomId;
		this.createPoll = createPoll;
	}

	@Override
	protected void onInitialize() {
		header(new ResourceModel("37"));
		setCloseOnEscapeKey(false);
		setBackdrop(Backdrop.STATIC);
		setUseCloseHandler(true);

		add(selForm = new PollSelectForm("selForm"));
		add(dispForm = new PollResultsForm("dispForm"));
		addButton(OmModalCloseButton.of());
		addButton(close = new BootstrapAjaxLink<>("button", null, Buttons.Type.Outline_Danger, new ResourceModel("1418")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				Long id = dispForm.getModelObject().getId();
				pollDao.close(roomId);
				selForm.updateModel(target);

				RoomPoll p = pollDao.get(id);
				selForm.select.setModelObject(p);
				dispForm.updateModel(p, true, target);
				sendRoom(new RoomMessage(roomId, findParent(MainPanel.class).getClient(), RoomMessage.Type.pollUpdated));
				close(target);
			}
		});
		close.setIconType(FontAwesome5IconType.times_s).add(newOkCancelDangerConfirm(this, getString("1419")));
		close.setOutputMarkupId(true).setOutputMarkupPlaceholderTag(true);
		addButton(delete = new BootstrapAjaxLink<>("button", null, Buttons.Type.Outline_Danger, new ResourceModel("1420")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				pollDao.delete(dispForm.getModelObject());
				selForm.updateModel(target);
				dispForm.updateModel(selForm.select.getModelObject(), true, target);
				sendRoom(new RoomMessage(roomId, findParent(MainPanel.class).getClient(), RoomMessage.Type.pollUpdated));
				close(target);
			}
		});
		delete.setIconType(FontAwesome5IconType.times_s).add(newOkCancelDangerConfirm(this, getString("1421")));
		delete.setOutputMarkupId(true).setOutputMarkupPlaceholderTag(true);
		addButton(clone = new BootstrapAjaxLink<>("button", null, Buttons.Type.Outline_Danger, new ResourceModel("poll.clone")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				RoomPoll rp = dispForm.getModelObject();
				RoomPoll nrp = new RoomPoll();
				nrp.setCreator(userDao.get(getUserId()));
				nrp.setName(rp.getName());
				nrp.setQuestion(rp.getQuestion());
				nrp.setType(rp.getType());
				nrp.setRoom(rp.getRoom());
				createPoll.setModelObject(nrp);
				createPoll.setModelObject(nrp);
				target.add(createPoll.getForm());
				createPoll.show(target);
				close(target);
			}
		});
		clone.setOutputMarkupId(true).setOutputMarkupPlaceholderTag(true);
		super.onInitialize();
	}

	public void updateModel(IPartialPageRequestHandler target, boolean moderator) {
		selForm.updateModel(target);
		this.moderator = moderator;
		RoomPoll p = selForm.select.getModelObject();
		dispForm.updateModel(p, false, target);
		StringBuilder builder = new StringBuilder();
		builder.append("$('#").append(PollResultsDialog.this.getMarkupId()).append("').on('dialogopen', function( event, ui ) {");
		builder.append(getScript(barChart(p)));
		builder.append("});");

		target.appendJavaScript(builder.toString());
	}

	private StringBuilder getScript(Chart<?> chart) {
		StringBuilder builder = new StringBuilder();
		builder.append("$('#").append(chartDiv.getMarkupId()).append("').html(''); ");
		builder.append("$.jqplot('").append(chartDiv.getMarkupId()).append("', ");
		builder.append(chart.getChartData().toJsonString());
		builder.append(", ");
		builder.append(JqPlotUtils.jqPlotToJson(chart.getChartConfiguration()));
		builder.append(");");
		return builder;
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(JavaScriptHeaderItem.forReference(JqPlotJavascriptResourceReference.get()));
		response.render(CssHeaderItem.forReference(JqPlotCssResourceReference.get()));
		Chart<?> c1 = new PieChart<>(null);
		c1.getChartConfiguration().setHighlighter(new Highlighter());
		for (Chart<?> chart : new Chart<?>[]{c1, new BarChart<Integer>(null)}) {
			for (String resource : JqPlotUtils.retriveJavaScriptResources(chart)) {
				response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(JqPlotBehavior.class, removeMinified(resource))));
			}
		}
	}

	private static String removeMinified(String _name) {
		String name = _name;
		int idxOfExtension = name.lastIndexOf('.');
		if (idxOfExtension > -1) {
			String extension = name.substring(idxOfExtension);
			name = name.substring(0, name.length() - extension.length() + 1);
			if (name.endsWith(".min.")) {
				name = name.substring(0, name.length() - 5) + extension;
			}
		}
		return name;
	}

	@Override
	public Modal<RoomPoll> show(IPartialPageRequestHandler handler) {
		opened = true;
		return super.show(handler);
	}

	@Override
	public void onClose(IPartialPageRequestHandler handler) {
		opened = false;
		super.onClose(handler);
	}

	public boolean isOpened() {
		return opened;
	}

	private String[] getTicks(RoomPoll p) {
		return p != null && RoomPoll.Type.numeric == p.getType()
				? new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"}
				: new String[] {getString("35"), getString("34")};
	}

	private static Integer[] initValues(int size) {
		Integer[] values = new Integer[size];
		for (int i = 0; i < size; ++i) {
			values[i] = 0;
		}
		return values;
	}

	private static Integer[] getValues(RoomPoll p) {
		Integer[] values = initValues(p != null && RoomPoll.Type.numeric == p.getType() ? 10 : 2);
		if (p != null) {
			for (RoomPollAnswer a : p.getAnswers()) {
				if (RoomPoll.Type.numeric == p.getType()) {
					values[a.getPointList() - 1] ++;
				} else {
					values[Boolean.FALSE.equals(a.getAnswer()) ? 0 : 1] ++;
				}
			}
		}
		return values;
	}

	private BarChart<Integer> barChart(RoomPoll p) {
		String[] ticks = getTicks(p);
		BarChart<Integer> barChart = new BarChart<>(null);
		barChart.addValue(Arrays.asList(getValues(p)));

		barChart.getSeriesDefaults()
				.setRendererOptions(new RendererOptions().setHighlightMouseDown(true).setShowDataLabels(true)
						.setFill(false).setSliceMargin(4).setLineWidth(5).setBarDirection("horizontal"));

		Highlighter h = new Highlighter();
		h.setShow(true);
		h.setFormatString("%s, %P");
		h.setTooltipLocation(Location.ne);
		h.setShowTooltip(true);
		h.setUseAxesFormatters(false);

		ChartConfiguration<Long> cfg = barChart.getChartConfiguration();
		cfg.setLegend(null).setHighlighter(h);
		cfg.axesInstance().setXaxis(null);
		cfg.axesInstance().yAxisInstance().setTicks(ticks).setRenderer(JqPlotResources.CategoryAxisRenderer);
		return barChart;
	}

	private class PollSelectForm extends Form<RoomPoll> {
		private static final long serialVersionUID = 1L;
		private DropDownChoice<RoomPoll> select;

		PollSelectForm(String id) {
			super(id);
		}

		@Override
		protected void onInitialize() {
			super.onInitialize();
			add((select = new DropDownChoice<>("polls", Model.of((RoomPoll)null), new ArrayList<RoomPoll>(), new ChoiceRenderer<RoomPoll>() {
				private static final long serialVersionUID = 1L;

				@Override
				public Object getDisplayValue(RoomPoll object) {
					return object == null ? "" : String.format("%s%s", object.getName(), object.isArchived() ? "" : String.format(" (%s)", getString("1413")));
				}

				@Override
				public String getIdValue(RoomPoll object, int index) {
					return object == null ? "" : "" + object.getId();
				}
			})).add(new AjaxFormComponentUpdatingBehavior("change") {
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target) {
					dispForm.updateModel(select.getModelObject(), true, target);
				}
			}));
			updateModel(null);
		}

		public void updateModel(IPartialPageRequestHandler handler) {
			List<RoomPoll> list = new ArrayList<>();
			RoomPoll p = pollDao.getByRoom(roomId);
			if (p != null) {
				list.add(p);
			}
			list.addAll(pollDao.getArchived(roomId));
			select.setChoices(list);
			select.setModelObject(list.isEmpty() ? null : list.get(0));
			if (handler != null) {
				handler.add(this);
			}
		}
	}

	private class PollResultsForm extends Form<RoomPoll> {
		private static final long serialVersionUID = 1L;
		private String chartSimple;
		private String chartPie;
		private final Label name = new Label("name", Model.of((String)null));
		private final Label question = new Label("question", Model.of((String)null));
		private final Label count = new Label("count", Model.of(0));
		private DropDownChoice<String> chartType;

		PollResultsForm(String id) {
			super(id, Model.of((RoomPoll)null));
			setOutputMarkupId(true);
		}

		@Override
		protected void onInitialize() {
			add(chartDiv.setOutputMarkupId(true));
			chartSimple = getString("1414");
			chartPie = getString("1415");
			add(name, question, count);
			chartType = new DropDownChoice<>("chartType", Model.of(chartSimple), Arrays.asList(chartSimple, chartPie));
			add(chartType.add(new AjaxFormComponentUpdatingBehavior("change") {
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target) {
					redraw(target);
				}
			}));
			super.onInitialize();
		}

		public void updateModel(RoomPoll poll, boolean redraw, IPartialPageRequestHandler handler) {
			setModelObject(poll);
			name.setDefaultModelObject(poll == null ? "" : VoteDialog.getName(this, poll.getCreator()));
			question.setDefaultModelObject(poll == null ? "" : poll.getQuestion());
			count.setDefaultModelObject(poll == null ? 0 : poll.getAnswers().size());
			handler.add(this);
			close.setVisible(moderator && (poll != null && !poll.isArchived()));
			clone.setVisible(moderator && (poll != null && poll.isArchived()));
			delete.setVisible(moderator);
			handler.add(close, clone, delete);
			if (redraw) {
				redraw(handler);
			}
		}

		private void redraw(IPartialPageRequestHandler handler) {
			RoomPoll poll = getModelObject();
			Chart<?> chart = chartSimple.equals(chartType.getModelObject()) ? barChart(poll) : pieChart(poll);
			handler.appendJavaScript(getScript(chart));
		}

		private PieChart<Integer> pieChart(RoomPoll p) {
			PieChart<Integer> pieChart = new PieChart<>(null);
			String[] ticks = getTicks(p);
			Integer[] values = getValues(p);
			for (int i = 0; i < values.length; ++i) {
				pieChart.addValue(ticks[i], values[i]);
			}

			pieChart.getSeriesDefaults().setRendererOptions(new RendererOptions().setHighlightMouseDown(true)
					.setShowDataLabels(true).setFill(false).setSliceMargin(4).setLineWidth(5));

			Highlighter h = new Highlighter();
			h.setShow(true);
			h.setFormatString("%s, %P");
			h.setTooltipLocation(Location.ne);
			h.setShowTooltip(true);
			h.setUseAxesFormatters(false);

			pieChart.getChartConfiguration().setLegend(null).setHighlighter(h);
			return pieChart;
		}
	}
}
