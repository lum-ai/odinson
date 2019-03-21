import React, { Component } from 'react';
import {
  ButtonGroup,
  Button,
  Classes,
  Tooltip
} from '@blueprintjs/core';

export default class PageNavigation extends Component {
  constructor(props) {
    super(props);
  }

  render() {
    if (this.props.totalPages < 1) {
      return null
    }
    return (
      <div className='navigation'>
      <hr></hr>
        <ButtonGroup minimal={true} large={true}>
          <Tooltip content='Go to first page'>
            <Button
              icon='chevron-backward'
              disabled={this.props.currentPage === 1}
              onClick={this.props.handleHeadClick}
            />
          </Tooltip>
          <Tooltip content='Go to previous page'>
            <Button
              icon='chevron-left'
              disabled={this.props.currentPage === 1}
              onClick={this.props.handleLeftClick}
            />
          </Tooltip>
          <div className='pageNumbers'>
            {this.props.currentPage} / {this.props.totalPages}
          </div>
          <Tooltip content='Go to next page'>
            <Button
              icon='chevron-right'
              disabled={this.props.currentPage == this.props.totalPages}
              onClick={this.props.handleRightClick}
            />
          </Tooltip>
          <Button disabled={true}/>
          {/*<Tooltip content='Not implemented yet' className={Classes.DARK}>
            <Button
              icon='chevron-forward'
              //disabled={this.props.currentPage == this.props.totalPages}
              disabled={true}
              onClick={this.props.handleLastClick}
            ></Button>
          </Tooltip>*/}
        </ButtonGroup>
      </div>
    )
  }
}