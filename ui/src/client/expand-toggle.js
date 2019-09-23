import React from 'react';
import PropTypes from 'prop-types';

import {
  // AnchorButton,
  Button,
  // Card,
  // Classes,
  // Elevation,
  Tooltip,
  Position
} from '@blueprintjs/core';

// toggle button to expand all sentences to TAG representation or collapse to highlighted text
function ExpandToggle(props) {
  const {
    hidden,
    expanded,
    handleExpand,
    handleCollapse
  } = props;

  if (hidden) {
    return null;
  }

  if (expanded) {
    return (
      <Tooltip
        content="Hide annotated view"
        position={Position.LEFT}
      >
        <Button
          icon="collapse-all"
          minimal
          large
          onClick={() => handleCollapse()}
        >
          Collapse all
        </Button>
      </Tooltip>
    );
  }

  return (
    <Tooltip
      content="Show annotated view"
      position={Position.LEFT}
    >
      <Button
        icon="expand-all"
        minimal
        large
        onClick={() => handleExpand()}
      >
        Expand all
      </Button>
    </Tooltip>
  );
}

ExpandToggle.propTypes = {
  hidden: PropTypes.bool.isRequired,
  expanded: PropTypes.bool.isRequired,
  handleExpand: PropTypes.func.isRequired,
  handleCollapse: PropTypes.func.isRequired
};

export default ExpandToggle;
