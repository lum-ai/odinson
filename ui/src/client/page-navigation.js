import React from 'react';

import PropTypes from 'prop-types';

import {
  ButtonGroup,
  Button,
  // Classes,
  Tooltip,
  Position
} from '@blueprintjs/core';

function PageNavigation(props) {
  const {
    totalPages,
    currentPage,
    handleHeadClick,
    handleLeftClick,
    handleRightClick
  } = props;

  if (totalPages < 1) {
    return null;
  }
  return (
    <div className="navigation">
      <hr />
      <ButtonGroup
        minimal
        large
      >
        <Tooltip
          content="Go to first page"
          position={Position.TOP}
        >
          <Button
            icon="chevron-backward"
            disabled={currentPage === 1}
            onClick={handleHeadClick}
          />
        </Tooltip>
        <Tooltip
          content="Go to previous page"
          position={Position.TOP}
        >
          <Button
            icon="chevron-left"
            disabled={currentPage === 1}
            onClick={handleLeftClick}
          />
        </Tooltip>
        <div className="pageNumbers">
          {currentPage}
        </div>
        <Tooltip
          content="Go to next page"
          position={Position.TOP}
        >
          <Button
            icon="chevron-right"
            disabled={currentPage === totalPages}
            onClick={handleRightClick}
          />
        </Tooltip>
        <Button disabled />
        {/* <Tooltip content='Not implemented yet' className={Classes.DARK}>
          <Button
            icon='chevron-forward'
            //disabled={this.props.currentPage == this.props.totalPages}
            disabled={true}
            onClick={this.props.handleLastClick}
          ></Button>
        </Tooltip> */}
      </ButtonGroup>
    </div>
  );
}

PageNavigation.propTypes = {
  totalPages: PropTypes.number,
  currentPage: PropTypes.number.isRequired,
  handleHeadClick: PropTypes.func.isRequired,
  handleLeftClick: PropTypes.func.isRequired,
  handleRightClick: PropTypes.func.isRequired
};

PageNavigation.defaultProps = {
  totalPages: null
};

export default PageNavigation;
