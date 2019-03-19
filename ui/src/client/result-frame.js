import React, { Component } from 'react';
import TAG from "text-annotation-graphs";
import {
  ButtonGroup,
  Button,
  Classes,
  Tooltip
} from "@blueprintjs/core";

const config = require('../../config');

export default class ResultFrame extends Component {
  constructor(props) {
    super(props);
    this.state = {
      'expanded': props.expanded
    };
    
    this.handleExpand = this.handleExpand.bind(this);
    this.handleCollapse = this.handleCollapse.bind(this);
  }

  handleExpand () {
    this.setState({
      'expanded': true
    });
  }

  handleCollapse () {
    this.setState({
      'expanded': false
    });
  }

  render() {
    if(this.state.expanded) {
      return (
        <div className="sentenceResults">
          <div className="row">
            <Tooltip content="See less detail" className={Classes.DARK}>
              <Button
                icon="collapse-all"
                minimal={true}
                large={true}
                onClick={() => this.setState({'expanded': false})}
              />
            </Tooltip>
            <h3>Sentence ID: {this.props.odinsonDocId}</h3>
          </div>
          <OdinsonTAG
            odinsonDocId={this.props.odinsonDocId}
            odinsonJson={this.props.odinsonJson}
          />
        </div>
      )
    } else {
      return (
        <div className="sentenceResults">
          <div className="row">
            <Tooltip content="See more detail" className={Classes.DARK}>
              <Button
                icon="expand-all"
                minimal={true}
                large={true}
                onClick={() => this.setState({'expanded': true})}
              />
            </Tooltip>
            <h3>Sentence ID: {this.props.odinsonDocId}</h3>
          </div>
          <OdinsonHighlight
            odinsonDocId={this.props.odinsonDocId}
            odinsonJson={this.props.odinsonJson}
          />
        </div>
      )
    }
  }
}

class OdinsonHighlight extends Component {
  constructor(props) {
    super(props);
  }

  render() {
    return (
      <div className="highlights">
        {this.props.odinsonJson.sentence.words.join(" ")}
      </div>
    )
  }
}

class OdinsonTAG extends Component {
  constructor(props) {
    super(props);
    this.tagRef      = React.createRef();
    this.tagInstance = null;
  }

  componentDidMount() {
    const odinsonTAG = TAG.tag({
      container: this.tagRef.current,
      data: this.props.odinsonJson,
      format: "odinson",
      // Overrides for default options
      options: {
        showTopArgLabels: config.tag.showTopArgLabels,
        bottomLinkCategory: config.tag.bottomLinkCategory,
        linkSlotInterval: config.tag.linkSlotInterval,
        compactRows: config.tag.compactRows
      }
    });
    this.tagInstance = odinsonTAG;
  }

  shouldComponentUpdate() {
    return false;
  }

  render() {

    return (
      // TODO: add div and dropdown button allowing one to reconfigure TAG elements
      <div ref={this.tagRef} className="scoreDoc"></div>
    )
  }
}

